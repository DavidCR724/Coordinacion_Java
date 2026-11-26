package mx.edu.itoaxaca.coordinadito.control;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import mx.edu.itoaxaca.coordinadito.control.vista.ListaSolicitudes;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.SolicitudFila;
import mx.edu.itoaxaca.coordinadito.control.vista.TramiteVista;
import modelo.bean.FiltroBandeja;
import modelo.bean.Pagina;
import modelo.bean.ResumenBandeja;
import modelo.bean.SolicitudService;
import modelo.bean.TramiteService;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Prioridad;
import modelo.entity.Solicitud;
import modelo.entity.Tramite;

/**
 * «control» Bandeja de atención de coordinación (vista/bandeja.xhtml, HU 2.1, 3.1 y 3.4).
 *
 * Los filtros se aplican en la base (SolicitudRepository.bandeja): el orden es siempre
 * prioridad más alta primero y, dentro de la misma prioridad, la más antigua primero.
 */
@Named
@ViewScoped
public class BandejaControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private SolicitudService solicitudService;

    @EJB
    private TramiteService tramiteService;

    // Filtros (comp-filtros-bandeja)
    /** "activas" o "finalizadas" (selector de comp-filtros-bandeja). */
    private String estado = ListaSolicitudes.ACTIVAS;
    private String idTramite;
    private String prioridadMinima;
    private String texto;
    /** HU 3.4: rango de fechas de registro (opcional). */
    private LocalDate desde;
    private LocalDate hasta;
    /** HU 3.1: "prioridad" (lo normal) o "fecha" (solo por antigüedad). */
    private String orden = "prioridad";
    /** ¿Se ve el panel «Más filtros»? */
    private boolean masFiltros;

    private List<SolicitudFila> lista = new ArrayList<>();
    private long total;
    private ResumenBandeja resumen;
    private List<TramiteVista> tramites;

    @PostConstruct
    void iniciar() {
        tramites = new ArrayList<>();
        for (Tramite t : tramiteService.todos()) {
            tramites.add(new TramiteVista(t));
        }
        buscar();
    }

    /** Aplica los filtros (botón «Buscar» y cambios de los selectores). */
    public void buscar() {
        FiltroBandeja f = new FiltroBandeja();
        f.setTamano(SolicitudesControl.MAXIMO);
        // Sin estados, el servicio trae las abiertas (las «Activas»).
        if (ListaSolicitudes.FINALIZADAS.equals(estado)) {
            f.setEstados(EnumSet.of(EstadoSolicitud.FINALIZADA, EstadoSolicitud.RECHAZADA));
        }
        if (idTramite != null && !idTramite.isEmpty()) {
            f.setIdTramite(UUID.fromString(idTramite));
        }
        if (prioridadMinima != null && !prioridadMinima.isEmpty()) {
            f.setPrioridadMinima(Prioridad.valueOf(prioridadMinima));
        }
        f.setTexto(texto);
        f.setDesde(desde);
        f.setHasta(hasta);
        f.setPorFecha("fecha".equals(orden));
        Pagina<Solicitud> p = Mensajes.intentar(() -> solicitudService.bandeja(yo(), f));
        lista = new ArrayList<>();
        total = 0;
        if (p != null) {
            for (Solicitud s : p.getElementos()) {
                lista.add(new SolicitudFila(s));
            }
            total = p.getTotal();
        }
        resumen = Mensajes.intentar(() -> solicitudService.resumen(yo()));
    }

    public void limpiar() {
        estado = ListaSolicitudes.ACTIVAS;
        idTramite = null;
        prioridadMinima = null;
        texto = null;
        desde = null;
        hasta = null;
        orden = "prioridad";
        buscar();
    }

    public List<SolicitudFila> getLista() { return lista; }
    public long getTotal() { return total; }
    public boolean isRecortada() { return total > lista.size(); }
    public ResumenBandeja getResumen() { return resumen; }
    public List<TramiteVista> getTramites() { return tramites; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getIdTramite() { return idTramite; }
    public void setIdTramite(String idTramite) { this.idTramite = idTramite; }
    public String getPrioridadMinima() { return prioridadMinima; }
    public void setPrioridadMinima(String prioridadMinima) { this.prioridadMinima = prioridadMinima; }
    public boolean isMasFiltros() { return masFiltros; }
    public void alternarFiltros() { masFiltros = !masFiltros; }

    /** Cuántos filtros de «Más filtros» están puestos (se muestra en el botón aunque esté plegado). */
    public int getFiltrosActivos() {
        int n = 0;
        if (idTramite != null && !idTramite.isEmpty()) n++;
        if (prioridadMinima != null && !prioridadMinima.isEmpty()) n++;
        if (desde != null) n++;
        if (hasta != null) n++;
        if ("fecha".equals(orden)) n++;
        return n;
    }

    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }
    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta = hasta; }
    public String getOrden() { return orden; }
    public void setOrden(String orden) { this.orden = "fecha".equals(orden) ? "fecha" : "prioridad"; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
}
