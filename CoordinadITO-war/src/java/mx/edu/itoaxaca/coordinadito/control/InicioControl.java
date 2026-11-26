package mx.edu.itoaxaca.coordinadito.control;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import mx.edu.itoaxaca.coordinadito.control.vista.AvisoVista;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import modelo.bean.NotificacionService;
import modelo.entity.Notificacion;

/**
 * «control» Inicio del estudiante (vista/inicio.xhtml): su bandeja de notificaciones.
 * Coordinación y jefatura no pasan por aquí: sesionControl.inicioDelRol las manda a la bandeja
 * y al seguimiento.
 */
@Named
@RequestScoped
public class InicioControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    private static final int RECIENTES = 5;

    @EJB private NotificacionService notificacionService;

    private final List<AvisoVista> avisos = new ArrayList<>();

    /** Solo el estudiante tiene inicio propio (ver sesionControl.inicioDelRol). */
    @PostConstruct
    void cargar() {
        if ("student".equals(sesion.getRol())) {
            // Sus solicitudes y contadores vienen de solicitudesControl (la sección
            // «Mis solicitudes» del inicio); aquí solo se cargan las notificaciones.
            for (Notificacion n : notificacionService.recientes(yo(), 20)) {
                avisos.add(new AvisoVista(n));
            }
        }
    }

    /** «Marcar todo como leído» de la bandeja de entrada del estudiante. */
    public String marcarTodas() {
        if (Mensajes.intentar(() -> notificacionService.marcarLeidas(yo()))) {
            sesion.olvidarSinLeer();
        }
        return Mensajes.redirigir("/vista/inicio");
    }


    public List<AvisoVista> getAvisos() { return avisos; }
    /** Pestaña «Sin leer» de la bandeja: las más recientes (el total va en el globo de la campana). */
    public List<AvisoVista> getAvisosSinLeer() {
        List<AvisoVista> r = new ArrayList<>();
        for (AvisoVista a : avisos) {
            if (a.isSinLeer() && r.size() < RECIENTES) {
                r.add(a);
            }
        }
        return r;
    }
    /** Pestaña «Todas las actualizaciones»: solo las más recientes; el resto en Notificaciones. */
    public List<AvisoVista> getAvisosRecientes() {
        return avisos.size() > RECIENTES ? avisos.subList(0, RECIENTES) : avisos;
    }
}
