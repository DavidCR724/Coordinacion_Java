package mx.edu.itoaxaca.coordinadito.control.acceso;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;
import modelo.bean.Identidad;

/**
 * Inicio de sesión con la cuenta institucional: OpenID Connect, flujo «Authorization Code» con PKCE.
 *
 * Analogía: el sistema no revisa contraseñas; manda al alumno a la ventanilla del instituto
 * (el proveedor), que le entrega un vale (code). El sistema canjea el vale por una credencial
 * firmada (id_token) y solo confía en ella si la firma, el emisor, el destinatario, la vigencia
 * y el número de trámite (nonce) coinciden.
 */
@ApplicationScoped
public class ClienteOidc {

    private static final long TOLERANCIA_SEG = 120;
    private static final SecureRandom AZAR = new SecureRandom();

    @Inject
    private ConfiguracionOidc config;

    /** Lo que se guarda en la sesión entre la ida al proveedor y el regreso. */
    public static final class Intento implements Serializable {
        private static final long serialVersionUID = 1L;
        final String estado = aleatorio();
        final String nonce = aleatorio();
        final String verificador = aleatorio() + aleatorio();
        final String redireccion;

        Intento(String redireccion) {
            this.redireccion = redireccion;
        }
    }

    public Intento nuevoIntento(String redireccionPorDefecto) {
        String r = config.getRedireccion();
        return new Intento(r == null || r.trim().isEmpty() ? redireccionPorDefecto : r);
    }

    /** URL del proveedor a la que se manda el navegador. */
    public String urlAutorizacion(Intento i) {
        return config.getAutorizacion() + (config.getAutorizacion().contains("?") ? "&" : "?")
                + "response_type=code"
                + "&client_id=" + url(config.getCliente())
                + "&redirect_uri=" + url(i.redireccion)
                + "&scope=" + url("openid email profile")
                + "&state=" + url(i.estado)
                + "&nonce=" + url(i.nonce)
                + "&code_challenge=" + url(sha256(i.verificador))
                + "&code_challenge_method=S256";
    }

    /** Valida el regreso del proveedor y devuelve la identidad que viene en el id_token. */
    public Identidad canjear(Intento i, String estado, String codigo) {
        if (i == null || estado == null || !i.estado.equals(estado)) {
            throw new IllegalArgumentException("El regreso del proveedor no corresponde a este intento de acceso.");
        }
        StringBuilder f = new StringBuilder("grant_type=authorization_code")
                .append("&code=").append(url(codigo))
                .append("&redirect_uri=").append(url(i.redireccion))
                .append("&client_id=").append(url(config.getCliente()))
                .append("&code_verifier=").append(url(i.verificador));
        String secreto = config.getSecreto();
        if (secreto != null && !secreto.isEmpty()) {
            f.append("&client_secret=").append(url(secreto));
        }
        JsonObject respuesta = ConfiguracionOidc.Http.enviar(config.getToken(), f.toString());
        JsonObject datos = verificar(respuesta.getString("id_token"), i.nonce);
        return new Identidad(correo(datos), datos.getString("given_name", null), datos.getString("family_name", null));
    }

    // ---------------------------------------------------------------------

    /** Revisa firma RS256, emisor, destinatario, vigencia y nonce. Devuelve los datos (claims). */
    JsonObject verificar(String jwt, String nonce) {
        String[] partes = jwt.split("\\.");
        if (partes.length != 3) {
            throw new IllegalArgumentException("Credencial con formato inválido.");
        }
        JsonObject cabecera = leer(partes[0]);
        JsonObject datos = leer(partes[1]);
        if (!"RS256".equals(cabecera.getString("alg", ""))) {
            throw new IllegalArgumentException("Algoritmo de firma no admitido.");
        }
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(config.llave(cabecera.getString("kid", "")));
            s.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(Base64.getUrlDecoder().decode(partes[2]))) {
                throw new IllegalArgumentException("La firma de la credencial no es válida.");
            }
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("No se pudo verificar la credencial.", e);
        }
        long ahora = System.currentTimeMillis() / 1000;
        exigir(config.getEmisor().equals(datos.getString("iss", "")), "emisor");
        exigir(destinatario(datos), "destinatario");
        exigir(datos.getJsonNumber("exp").longValue() + TOLERANCIA_SEG > ahora, "vigencia");
        exigir(nonce.equals(datos.getString("nonce", "")), "nonce");
        return datos;
    }

    private boolean destinatario(JsonObject datos) {
        JsonValue aud = datos.get("aud");
        if (aud == null) {
            return false;
        }
        if (aud.getValueType() == JsonValue.ValueType.ARRAY) {
            return aud.asJsonArray().contains(Json.createValue(config.getCliente()));
        }
        return config.getCliente().equals(datos.getString("aud"));
    }

    /** Google usa «email»; Microsoft Entra, «email», «preferred_username» o «upn». */
    private static String correo(JsonObject d) {
        for (String clave : new String[] {"email", "preferred_username", "upn"}) {
            String v = d.getString(clave, null);
            if (v != null && v.contains("@")) {
                return v;
            }
        }
        throw new IllegalArgumentException("El proveedor no entregó un correo.");
    }

    private static void exigir(boolean ok, String que) {
        if (!ok) {
            throw new IllegalArgumentException("La credencial no es válida (" + que + ").");
        }
    }

    private static JsonObject leer(String base64) {
        try (JsonReader r = Json.createReader(new ByteArrayInputStream(Base64.getUrlDecoder().decode(base64)))) {
            return r.readObject();
        }
    }

    private static String aleatorio() {
        byte[] b = new byte[24];
        AZAR.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String sha256(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String url(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
