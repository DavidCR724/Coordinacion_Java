package mx.edu.itoaxaca.coordinadito.control;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.faces.FacesException;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerFactory;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.faces.event.ExceptionQueuedEventContext;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * «control» Atiende la «vista caducada» de JSF en lugar de mostrar el error 500.
 *
 * Qué es: JSF guarda en la sesión el estado de cada página abierta (unas 15 por sesión). Si la
 * persona deja una página abierta más de 30 minutos (la sesión venció) o abre muchas pestañas,
 * al pulsar un botón el servidor ya no tiene ese estado y lanza ViewExpiredException.
 *
 * Qué hace aquí:
 *  - Sesión todavía abierta  → recarga la misma página (misma URL, con su ?folio=) y avisa que
 *    había caducado, para que repita la acción.
 *  - Sesión terminada        → manda al inicio de sesión con el aviso «Tu sesión terminó».
 * Funciona también en peticiones AJAX: ExternalContext.redirect responde con una redirección
 * parcial que PrimeFaces sigue.
 *
 * Se registra en WEB-INF/faces-config.xml (FabricaManejadorExcepciones).
 */
public class ManejadorExcepciones extends ExceptionHandlerWrapper {

    private static final Logger LOG = Logger.getLogger(ManejadorExcepciones.class.getName());
    private static final String LOGIN = "/vista/login.xhtml";

    /** La fábrica que JSF usa para crear el manejador. */
    public static class Fabrica extends ExceptionHandlerFactory {

        public Fabrica(ExceptionHandlerFactory padre) {
            super(padre);
        }

        @Override
        public ExceptionHandler getExceptionHandler() {
            return new ManejadorExcepciones(getWrapped().getExceptionHandler());
        }
    }

    public ManejadorExcepciones(ExceptionHandler envuelto) {
        super(envuelto);
    }

    @Override
    public void handle() throws FacesException {
        Iterator<ExceptionQueuedEvent> eventos = getUnhandledExceptionQueuedEvents().iterator();
        while (eventos.hasNext()) {
            ExceptionQueuedEventContext ctx = (ExceptionQueuedEventContext) eventos.next().getSource();
            ViewExpiredException caducada = buscar(ctx.getException());
            if (caducada == null) {
                continue;
            }
            eventos.remove();
            redirigir(ctx.getContext(), caducada);
        }
        getWrapped().handle();
    }

    private static ViewExpiredException buscar(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof ViewExpiredException) {
                return (ViewExpiredException) c;
            }
        }
        return null;
    }

    private static void redirigir(FacesContext fc, ViewExpiredException e) {
        ExternalContext ec = fc.getExternalContext();
        boolean conSesion = sesionAbierta(ec);
        String destino;
        FacesMessage aviso;
        if (conSesion) {
            destino = mismaPagina(ec, e.getViewId());
            aviso = new FacesMessage(FacesMessage.SEVERITY_WARN, "La página había caducado",
                    "La recargamos para que tengas los datos al día. Repite lo que estabas haciendo.");
        } else {
            destino = ec.getRequestContextPath() + LOGIN;
            aviso = new FacesMessage(FacesMessage.SEVERITY_WARN, "Tu sesión terminó",
                    "Por seguridad, la sesión se cierra tras 30 minutos sin actividad. Vuelve a entrar.");
        }
        try {
            fc.addMessage(null, aviso);
            ec.getFlash().setKeepMessages(true);
            ec.redirect(destino);
            fc.responseComplete();
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.WARNING, "No se pudo redirigir tras una vista caducada", ex);
        }
    }

    private static boolean sesionAbierta(ExternalContext ec) {
        if (ec.getSession(false) == null) {
            return false;
        }
        try {
            return CDI.current().select(SesionControl.class).get().isAutenticado();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * La URL que la persona tenía abierta (con su ?folio=, ?id=...). Se toma del encabezado Referer
     * solo si apunta a esta misma aplicación; si no, se usa la vista sin parámetros.
     */
    private static String mismaPagina(ExternalContext ec, String viewId) {
        String raiz = ec.getRequestContextPath() + "/";
        String referer = ec.getRequestHeaderMap().get("Referer");
        if (referer != null) {
            String origen = ec.getRequestScheme() + "://" + ec.getRequestServerName() + ":" + ec.getRequestServerPort();
            String sinPuerto = ec.getRequestScheme() + "://" + ec.getRequestServerName();
            for (String o : new String[] {origen, sinPuerto}) {
                if (referer.startsWith(o + raiz)) {
                    return referer.substring(o.length());
                }
            }
        }
        return viewId == null ? ec.getRequestContextPath() + LOGIN : ec.getRequestContextPath() + viewId;
    }
}
