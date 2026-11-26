package mx.edu.itoaxaca.coordinadito.control;

import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.UUID;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;

/**
 * Base de los beans de control: da acceso a la sesión sin que cada control la inyecte y la
 * consulte por su cuenta. Es Serializable porque los controles @ViewScoped viven en la sesión.
 */
public abstract class ControlBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    protected SesionControl sesion;

    /** Id del usuario en sesión (lo que reciben todos los servicios para revisar permisos). */
    protected UUID yo() {
        return sesion.getIdUsuario();
    }

    /** student | coordinator | head */
    protected String rol() {
        return sesion.getRol();
    }

    protected boolean esRol(String codigo) {
        return codigo.equals(rol());
    }

    /**
     * Ejecuta una operación del EJB y, si sale bien, avisa con un mensaje de éxito.
     * Es el patrón de todos los botones: «hazlo; si se pudo, dilo; si no, Mensajes ya explicó por qué».
     */
    protected boolean hecho(Runnable operacion, String titulo, String detalle) {
        if (Mensajes.intentar(operacion)) {
            Mensajes.info(titulo, detalle);
            return true;
        }
        return false;
    }

    /** Folio escrito como «ITO-0042», «42» o «0042» → 42; null si no es un número. */
    protected static Integer numeroDeFolio(String folio) {
        try {
            return folio == null ? null : Integer.valueOf(folio.trim().replaceFirst("(?i)^ito-0*", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
