package mx.edu.itoaxaca.coordinadito.control;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import mx.edu.itoaxaca.coordinadito.control.vista.AvisoVista;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import modelo.bean.NotificacionService;
import modelo.entity.Notificacion;

/** «control» Historial de notificaciones (vista/notificaciones.xhtml, HU 5.1 y 5.2). */
@Named
@ViewScoped
public class NotificacionesControl extends ControlBase {

    private static final long serialVersionUID = 1L;
    static final int MAXIMO = 100;

    @EJB
    private NotificacionService notificacionService;

    private List<AvisoVista> avisos;
    /** null = todas; "sinLeer" = solo las no leídas. */
    private String filtro;

    @PostConstruct
    void cargar() {
        avisos = new ArrayList<>();
        for (Notificacion n : notificacionService.recientes(yo(), MAXIMO)) {
            avisos.add(new AvisoVista(n));
        }
    }

    public void marcarTodas() {
        if (Mensajes.intentar(() -> notificacionService.marcarLeidas(yo()))) {
            sesion.olvidarSinLeer();
            cargar();
        }
    }

    public List<AvisoVista> getAvisos() { return avisos; }

    public List<AvisoVista> getVisibles() {
        if (!"sinLeer".equals(filtro)) {
            return avisos;
        }
        List<AvisoVista> r = new ArrayList<>();
        for (AvisoVista a : avisos) {
            if (a.isSinLeer()) {
                r.add(a);
            }
        }
        return r;
    }

    public long getPendientes() {
        long n = 0;
        for (AvisoVista a : avisos) {
            if (a.isSinLeer()) {
                n++;
            }
        }
        return n;
    }

    public String getFiltro() { return filtro; }
    public void setFiltro(String filtro) { this.filtro = filtro; }
}
