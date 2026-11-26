package modelo.bean;

import acceso.datos.CodigoAccesoRepository;
import acceso.datos.UsuarioRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import modelo.entity.Accion;
import modelo.entity.CodigoAcceso;
import modelo.entity.Fechas;
import modelo.entity.Usuario;

/**
 * «servicio» Acceso con código por correo: la alternativa al proveedor institucional (OIDC).
 *
 * Analogía: es como la ficha que te da la caseta para entrar al estacionamiento. Se entrega
 * solo a quien la pide desde su correo institucional (prueba de que ese buzón es suyo), vale unos
 * minutos, sirve una vez y, si alguien intenta adivinarla muchas veces, se invalida.
 *
 * Reglas:
 *  - Solo correos @itoaxaca.edu.mx que podrían entrar: con cuenta activa, o estudiantes (número de
 *    control) que entrarán por padrón o por primer acceso. A los demás no se les manda nada, pero la
 *    pantalla responde igual («si tu correo puede entrar, te llegó un código»): así nadie puede usar
 *    el formulario para averiguar qué correos tienen cuenta.
 *  - 6 dígitos con SecureRandom; se guarda su huella SHA-256 con sal, nunca el código.
 *  - Vence en {@value #MINUTOS_VIGENCIA} minutos, sirve una vez y admite {@value #MAX_INTENTOS} intentos.
 *    Solo vale el último código pedido.
 *  - Límites: uno cada {@value #ESPERA_SEGUNDOS} s y {@value #MAX_POR_HORA} por hora por correo;
 *    {@value #MAX_POR_IP} cada 15 minutos por red (holgado: en el Tec muchas PCs salen con la misma IP).
 *  - Verificado el código, se entra por el mismo camino que OIDC: AccesoService.ingresar.
 */
@Stateless
public class CodigoAccesoService {

    static final int MINUTOS_VIGENCIA = 10;
    static final int MAX_INTENTOS = 5;
    static final int ESPERA_SEGUNDOS = 60;
    static final int MAX_POR_HORA = 5;
    static final int MAX_POR_IP = 60;

    private static final Pattern CORREO = Pattern.compile("[^@\\s]+@[^@\\s]+");
    private static final SecureRandom AZAR = new SecureRandom();
    private static final Logger LOG = Logger.getLogger(CodigoAccesoService.class.getName());

    /** Resultado de comprobar un código. */
    public enum Comprobacion { CORRECTO, INCORRECTO, AGOTADO, VENCIDO, SIN_CODIGO }

    @EJB private CodigoAccesoRepository codigos;
    @EJB private UsuarioRepository usuarios;
    @EJB private CorreoService correo;
    @EJB private AuditoriaService auditoria;

    /** ¿Se puede ofrecer el acceso con código? (hay sesión de correo en Payara). */
    public boolean disponible() {
        return correo.disponible();
    }

    /**
     * Paso 1: manda un código al correo, si ese correo puede entrar. La respuesta es la misma
     * en todos los casos (salvo correo mal escrito, límite de la red o falla del envío).
     */
    public void solicitar(String correoEscrito) {
        String direccion = normalizar(correoEscrito);
        String ip = ContextoPeticion.ip();
        OffsetDateTime ahora = Fechas.ahora();

        if (ip != null && codigos.enviadosDesde(ip, ahora.minusMinutes(15)) >= MAX_POR_IP) {
            throw new ReglaNegocioException("Se pidieron demasiados códigos desde tu red. Espera unos minutos e intenta de nuevo.");
        }
        Optional<Usuario> cuenta = usuarios.porCorreo(direccion);
        boolean puedeEntrar = cuenta.isPresent()
                ? cuenta.get().isActivo()
                : AccesoService.esNumeroControl(new Identidad(direccion, null, null));
        if (!puedeEntrar) {
            LOG.fine("Código no enviado: el correo no puede entrar");
            return;
        }
        Optional<CodigoAcceso> ultimo = codigos.ultimo(direccion);
        if (ultimo.isPresent() && !ultimo.get().isUsado() && ultimo.get().getCreadoEn().isAfter(ahora.minusSeconds(ESPERA_SEGUNDOS))) {
            return;   // ya se mandó uno hace menos de un minuto: ese sigue sirviendo
        }
        if (codigos.enviadosA(direccion, ahora.minusHours(1)) >= MAX_POR_HORA) {
            LOG.info("Código no enviado: límite por hora alcanzado para un correo");
            return;
        }

        String codigo = String.format(Locale.ROOT, "%06d", AZAR.nextInt(1000000));
        String sal = aleatorio(16);
        codigos.guardar(new CodigoAcceso(direccion, huella(sal, codigo), sal, MINUTOS_VIGENCIA, ip));
        try {
            correo.enviar(direccion, "CoordinadITO · Tu código de acceso", mensaje(codigo));
        } catch (CorreoService.CorreoNoEnviado e) {
            LOG.warning("No se pudo enviar el código de acceso: " + e.getMessage());
            // ReglaNegocioException deshace la transacción: el código no enviado no queda guardado.
            throw new ReglaNegocioException("No pudimos enviar el correo en este momento. Intenta de nuevo en unos minutos.");
        }
        auditoria.registrar(cuenta.orElse(null), Accion.CODIGO_ACCESO, "USUARIO", direccion);
    }

    /**
     * Paso 2: compara el código con el último enviado a ese correo. Corre en su propia transacción
     * para que el intento quede contado aunque el acceso falle después.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Comprobacion comprobar(String correoEscrito, String codigoEscrito) {
        String direccion = normalizar(correoEscrito);
        String codigo = codigoEscrito == null ? "" : codigoEscrito.replaceAll("\\s", "");
        Optional<CodigoAcceso> encontrado = codigos.ultimo(direccion);
        if (!encontrado.isPresent() || encontrado.get().isUsado()) {
            return Comprobacion.SIN_CODIGO;
        }
        CodigoAcceso c = encontrado.get();
        if (c.isVencido(Fechas.ahora())) {
            return Comprobacion.VENCIDO;
        }
        if (c.getIntentos() >= MAX_INTENTOS) {
            return Comprobacion.AGOTADO;
        }
        c.sumarIntento();
        boolean igual = MessageDigest.isEqual(
                huella(c.getSal(), codigo).getBytes(StandardCharsets.US_ASCII),
                c.getHuella().getBytes(StandardCharsets.US_ASCII));   // tiempo constante
        if (!igual) {
            return c.getIntentos() >= MAX_INTENTOS ? Comprobacion.AGOTADO : Comprobacion.INCORRECTO;
        }
        c.marcarUsado();
        return Comprobacion.CORRECTO;
    }

    // ---------------------------------------------------------------------

    /** Minúsculas y sin espacios; exige el dominio institucional. */
    static String normalizar(String correoEscrito) {
        String c = correoEscrito == null ? "" : correoEscrito.trim().toLowerCase(Locale.ROOT);
        if (!CORREO.matcher(c).matches() || !c.endsWith("@" + AccesoService.DOMINIO)) {
            throw new ReglaNegocioException("Escribe tu correo institucional @" + AccesoService.DOMINIO + ".");
        }
        return c;
    }

    private static String mensaje(String codigo) {
        return "Hola.\n\n"
                + "Tu código para entrar a CoordinadITO es:\n\n"
                + "        " + codigo.substring(0, 3) + " " + codigo.substring(3) + "\n\n"
                + "Vence en " + MINUTOS_VIGENCIA + " minutos y solo sirve una vez.\n\n"
                + "Si no lo pediste, ignora este correo: sin el código nadie puede entrar con tu cuenta.\n\n"
                + "Coordinación académica · Instituto Tecnológico de Oaxaca";
    }

    static String huella(String sal, String codigo) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((sal + codigo).getBytes(StandardCharsets.UTF_8));
            return hex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String aleatorio(int bytes) {
        byte[] b = new byte[bytes];
        AZAR.nextBytes(b);
        return hex(b);
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) {
            s.append(String.format("%02x", x));
        }
        return s.toString();
    }
}
