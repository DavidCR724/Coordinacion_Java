package modelo.bean;

import acceso.datos.NotificacionRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import modelo.entity.Fechas;
import modelo.entity.Notificacion;

/**
 * «servicio» El único punto por donde CoordinadITO manda correos.
 *
 * Dos usos:
 *  1. {@link #enviar(String, String, String)}: un correo inmediato (por ejemplo, el código de acceso).
 *  2. {@link #enviarPendientes()}: cada minuto manda hasta 20 notificaciones sin CORREO_ENVIADO_EN
 *     (HU 5.1) y las marca; si una falla, queda pendiente y se reintenta en el siguiente ciclo.
 *
 * Configuración (herramientas/configurar-correo.ps1): una sesión de correo de Payara con nombre JNDI
 * mail/coordinadito que apunta al SMTP (Gmail: smtp.gmail.com:587 con STARTTLS). La contraseña
 * (de aplicación, en Gmail) nunca está en el código ni en domain.xml en claro: es un alias de Payara
 * (coordinadito-correo). Se lee, en este orden, de:
 *    a) la propiedad mail.smtp.password de la sesión, si Payara ya resolvió el alias;
 *    b) la propiedad del sistema coordinadito.correo.clave (= ${ALIAS=coordinadito-correo}).
 * Si no hay sesión de correo, no manda nada: el resto del sistema funciona igual.
 */
@Stateless
public class CorreoService {

    static final String JNDI_CORREO = "mail/coordinadito";
    static final String PROPIEDAD_CLAVE = "coordinadito.correo.clave";
    private static final int LOTE = 20;
    private static final String NOMBRE_REMITENTE = "CoordinadITO";
    private static final Logger LOG = Logger.getLogger(CorreoService.class.getName());

    /** El correo no salió (sin configuración, SMTP rechazó, sin red...). El mensaje es para la bitácora. */
    public static class CorreoNoEnviado extends Exception {
        private static final long serialVersionUID = 1L;

        CorreoNoEnviado(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    @EJB
    private NotificacionRepository notificaciones;

    /** ¿Hay sesión de correo configurada? (No prueba la conexión: solo que exista.) */
    public boolean disponible() {
        return sesionDeCorreo() != null;
    }

    /**
     * Manda un correo de texto ahora mismo.
     * @throws CorreoNoEnviado si no hay configuración o el servidor SMTP lo rechaza.
     */
    public void enviar(String para, String asunto, String texto) throws CorreoNoEnviado {
        Session sesion = sesionDeCorreo();
        if (sesion == null) {
            throw new CorreoNoEnviado("No hay sesión de correo " + JNDI_CORREO + " en Payara.", null);
        }
        try {
            enviar(sesion, para, asunto, texto);
        } catch (MessagingException e) {
            throw new CorreoNoEnviado("El servidor de correo no aceptó el mensaje: " + causa(e), e);
        }
    }

    @Schedule(second = "0", minute = "*", hour = "*", persistent = false, info = "envio-correos")
    public void enviarPendientes() {
        Session sesion = sesionDeCorreo();
        if (sesion == null) {
            return;
        }
        List<Notificacion> lote = notificaciones.tomarPendientesDeCorreo(LOTE);
        for (Notificacion n : lote) {
            try {
                enviar(sesion, n.getDestinatario().getCorreo(), "CoordinadITO · " + n.getTitulo(), cuerpo(n));
                n.setCorreoEnviadoEn(Fechas.ahora());
            } catch (MessagingException e) {
                LOG.log(Level.WARNING, "No se pudo enviar el correo de la notificación " + n.getId() + ": " + causa(e));
                break;   // si el SMTP falla con una, casi seguro falla con todas: se reintenta en un minuto
            }
        }
    }

    private static String cuerpo(Notificacion n) {
        StringBuilder c = new StringBuilder()
                .append("Hola, ").append(n.getDestinatario().getNombres()).append(".\n\n")
                .append(n.getMensaje()).append("\n\n");
        if (n.getSolicitud() != null) {
            c.append("Solicitud: ").append(n.getSolicitud().getFolioTexto()).append('\n');
        }
        return c.append("\nEste es un aviso automático de CoordinadITO; no respondas a este correo.").toString();
    }

    private void enviar(Session sesion, String para, String asunto, String texto) throws MessagingException {
        MimeMessage m = new MimeMessage(sesion);
        String remitente = sesion.getProperty("mail.from");
        if (remitente != null) {
            try {
                m.setFrom(new InternetAddress(remitente, NOMBRE_REMITENTE, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                m.setFrom(new InternetAddress(remitente));
            }
        }
        m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(para));
        m.setSubject(asunto, "UTF-8");
        m.setText(texto, "UTF-8");
        m.setSentDate(new Date());

        String usuario = primero(sesion.getProperty("mail.smtp.user"), sesion.getProperty("mail.user"));
        String clave = clave(sesion);
        if (usuario != null && clave != null) {
            Transport.send(m, usuario, clave);   // autenticación explícita (Gmail la exige)
        } else {
            Transport.send(m);                   // SMTP sin autenticación (o con Authenticator de Payara)
        }
    }

    /** La contraseña ya resuelta, o null. Nunca se escribe en la bitácora. */
    private static String clave(Session sesion) {
        String enSesion = primero(sesion.getProperty("mail.smtp.password"), sesion.getProperty("mail.password"));
        if (resuelta(enSesion)) {
            return enSesion;
        }
        String enSistema = System.getProperty(PROPIEDAD_CLAVE);
        if (resuelta(enSistema)) {
            return enSistema;
        }
        if (enSesion != null || enSistema != null) {
            LOG.warning("La contraseña del correo es un alias que Payara no resolvió; revisa que exista el alias coordinadito-correo.");
        }
        return null;
    }

    private static boolean resuelta(String valor) {
        return valor != null && !valor.isEmpty() && !valor.startsWith("${");
    }

    private static String primero(String a, String b) {
        return a != null && !a.isEmpty() ? a : (b != null && !b.isEmpty() ? b : null);
    }

    private static String causa(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }

    private Session sesionDeCorreo() {
        try {
            return (Session) new InitialContext().lookup(JNDI_CORREO);
        } catch (NamingException e) {
            LOG.log(Level.FINE, "Sin sesión de correo {0}: los correos quedan pendientes", JNDI_CORREO);
            return null;
        }
    }
}
