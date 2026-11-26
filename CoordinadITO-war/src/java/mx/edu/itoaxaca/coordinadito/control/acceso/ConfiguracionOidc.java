package mx.edu.itoaxaca.coordinadito.control.acceso;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Datos del proveedor institucional de identidad (OpenID Connect).
 *
 * Se configura con propiedades del sistema de Payara, nunca en el código:
 * <pre>
 *   coordinadito.oidc.emisor       https://login.microsoftonline.com/&lt;tenant&gt;/v2.0  (o https://accounts.google.com)
 *   coordinadito.oidc.cliente      id de la aplicación registrada
 *   coordinadito.oidc.secreto      secreto de la aplicación (mejor como alias: ${ALIAS=coordinadito-oidc})
 *   coordinadito.oidc.redireccion  opcional; por defecto https://&lt;host&gt;/CoordinadITO-war/acceso/retorno
 * </pre>
 * El documento de descubrimiento y las llaves públicas (JWKS) se piden una vez y se guardan.
 */
@ApplicationScoped
public class ConfiguracionOidc {

    private static final int TIEMPO_MS = 10000;

    private volatile JsonObject descubrimiento;
    private final Map<String, PublicKey> llaves = new ConcurrentHashMap<>();

    public boolean isConfigurado() {
        return !vacio(propiedad("emisor")) && !vacio(propiedad("cliente"));
    }

    public String getCliente() { return propiedad("cliente"); }
    public String getSecreto() { return propiedad("secreto"); }
    public String getRedireccion() { return propiedad("redireccion"); }

    public String getEmisor() { return dato("issuer"); }
    public String getAutorizacion() { return dato("authorization_endpoint"); }
    public String getToken() { return dato("token_endpoint"); }

    /** Llave pública RSA con la que el proveedor firmó un token. Si no está, se recargan las llaves una vez. */
    public PublicKey llave(String kid) throws IOException {
        PublicKey k = llaves.get(kid);
        if (k == null) {
            cargarLlaves();
            k = llaves.get(kid);
        }
        if (k == null) {
            throw new IOException("El proveedor no publica la llave " + kid);
        }
        return k;
    }

    // ---------------------------------------------------------------------

    private String dato(String nombre) {
        JsonObject d = descubrimiento;
        if (d == null) {
            synchronized (this) {
                if (descubrimiento == null) {
                    String emisor = propiedad("emisor").replaceAll("/+$", "");
                    descubrimiento = Http.json(Http.abrir(emisor + "/.well-known/openid-configuration"));
                }
                d = descubrimiento;
            }
        }
        return d.getString(nombre);
    }

    private synchronized void cargarLlaves() {
        JsonObject jwks = Http.json(Http.abrir(dato("jwks_uri")));
        Base64.Decoder b64 = Base64.getUrlDecoder();
        try {
            KeyFactory fabrica = KeyFactory.getInstance("RSA");
            for (JsonObject k : jwks.getJsonArray("keys").getValuesAs(JsonObject.class)) {
                if ("RSA".equals(k.getString("kty", null)) && k.containsKey("kid")) {
                    BigInteger n = new BigInteger(1, b64.decode(k.getString("n")));
                    BigInteger e = new BigInteger(1, b64.decode(k.getString("e")));
                    llaves.put(k.getString("kid"), fabrica.generatePublic(new RSAPublicKeySpec(n, e)));
                }
            }
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Llaves del proveedor inválidas", ex);
        }
    }

    private static String propiedad(String nombre) {
        return System.getProperty("coordinadito.oidc." + nombre);
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Lo mínimo de HTTP que hace falta: GET de JSON y POST de formulario. */
    static final class Http {

        private Http() { }

        static HttpURLConnection abrir(String url) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(TIEMPO_MS);
                c.setReadTimeout(TIEMPO_MS);
                c.setRequestProperty("Accept", "application/json");
                return c;
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo contactar " + url, e);
            }
        }

        static JsonObject enviar(String url, String formulario) {
            HttpURLConnection c = abrir(url);
            c.setDoOutput(true);
            try {
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(formulario.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo contactar " + url, e);
            }
            return json(c);
        }

        static JsonObject json(HttpURLConnection c) {
            try {
                int codigo = c.getResponseCode();
                InputStream in = codigo < 400 ? c.getInputStream() : c.getErrorStream();
                try (JsonReader r = Json.createReader(in)) {
                    JsonObject o = r.readObject();
                    if (codigo >= 400) {
                        throw new IllegalStateException("El proveedor respondió " + codigo + ": " + o.getString("error", "")
                                + " " + o.getString("error_description", ""));
                    }
                    return o;
                }
            } catch (IOException e) {
                throw new IllegalStateException("Respuesta inválida de " + c.getURL(), e);
            } finally {
                c.disconnect();
            }
        }
    }
}
