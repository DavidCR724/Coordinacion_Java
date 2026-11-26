package mx.edu.itoaxaca.coordinadito.control;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.ListaSolicitudes;
import mx.edu.itoaxaca.coordinadito.control.vista.SolicitudFila;
import modelo.bean.FiltroBandeja;
import modelo.bean.Pagina;
import modelo.bean.SolicitudService;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Solicitud;

/**
 * «control» Lista de solicitudes (vista/solicitudes.xhtml, HU 2.3 y 2.8).
 *     Estudiante   → las suyas.
 *     Coordinación → todas las de sus carreras, abiertas y cerradas.
 *     Jefatura     → todas las de todas las carreras (solo consulta).
 */
@Named
@ViewScoped
public class SolicitudesControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    /** Tope de filas que trae la consulta (la tabla busca y ordena en el navegador). */
    static final int MAXIMO = 100;

    @EJB
    private SolicitudService solicitudService;

    private List<SolicitudFila> lista = new ArrayList<>();
    private long total;
    private ListaSolicitudes listado;

    @PostConstruct
    void cargar() {
        String rol = sesion.getRol();
        List<Solicitud> ss = null;
        if ("student".equals(rol)) {
            ss = Mensajes.intentar(() -> solicitudService.misSolicitudes(yo()));
        } else if ("coordinator".equals(rol) || "head".equals(rol)) {
            FiltroBandeja f = new FiltroBandeja();
            f.setEstados(EnumSet.allOf(EstadoSolicitud.class));
            f.setTamano(MAXIMO);
            Pagina<Solicitud> p = Mensajes.intentar(() -> solicitudService.bandeja(yo(), f));
            if (p != null) {
                ss = p.getElementos();
                total = p.getTotal();
            }
        }
        if (ss != null) {
            for (Solicitud s : ss) {
                lista.add(new SolicitudFila(s));
            }
            total = Math.max(total, lista.size());
        }
        listado = new ListaSolicitudes(lista);
    }

    /** La misma lista partida en «Activas» y «Finalizadas» (el selector de arriba de la tabla). */
    public ListaSolicitudes getListado() { return listado; }

    public List<SolicitudFila> getLista() { return lista; }
    /** true si hay más solicitudes de las que se muestran. */
    public boolean isRecortada() { return total > lista.size(); }
    public long getTotal() { return total; }
}
