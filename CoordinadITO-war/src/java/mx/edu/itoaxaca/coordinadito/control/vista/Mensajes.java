package mx.edu.itoaxaca.coordinadito.control.vista;

import jakarta.ejb.EJBException;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import modelo.bean.ReglaNegocioException;

/**
 * Traduce lo que responde el EJB a mensajes de PrimeFaces (p:growl / p:messages).
 *
 * Las ReglaNegocioException ya traen un texto pensado para el usuario («Escribe la constancia
 * del cambio.»); cualquier otro error se registra en el log de Payara y se muestra un mensaje genérico.
 */
public final class Mensajes {

    private static final Logger LOG = Logger.getLogger(Mensajes.class.getName());

    private Mensajes() { }

    public static void info(String resumen, String detalle) {
        agregar(FacesMessage.SEVERITY_INFO, resumen, detalle);
    }

    public static void error(String detalle) {
        agregar(FacesMessage.SEVERITY_ERROR, "No se pudo completar", detalle);
    }

    /**
     * Ejecuta una operación del EJB. Devuelve su resultado o null si falló (el mensaje ya quedó agregado).
     */
    public static <T> T intentar(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (ReglaNegocioException e) {
            error(e.getMessage());
        } catch (EJBException e) {
            Throwable causa = e.getCause() == null ? e : e.getCause();
            if (causa instanceof ReglaNegocioException) {
                error(causa.getMessage());
            } else {
                LOG.log(Level.SEVERE, "Error al llamar al EJB", e);
                error("Ocurrió un error en el servidor. Intenta de nuevo; si persiste, avisa a la coordinación.");
            }
        }
        FacesContext.getCurrentInstance().validationFailed();
        return null;
    }

    /** Igual que intentar(), para operaciones sin resultado. Devuelve true si salió bien. */
    public static boolean intentar(Runnable operacion) {
        return intentar(() -> {
            operacion.run();
            return Boolean.TRUE;
        }) != null;
    }

    private static void agregar(FacesMessage.Severity severidad, String resumen, String detalle) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, resumen, detalle));
    }

    /**
     * Navegación con redirect que conserva los mensajes ya agregados (viajan en el Flash).
     * Solo aquí se usa el Flash: en una respuesta AJAX normal no hace falta.
     */
    public static String redirigir(String vista) {
        FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
        return vista + "?faces-redirect=true";
    }
}
