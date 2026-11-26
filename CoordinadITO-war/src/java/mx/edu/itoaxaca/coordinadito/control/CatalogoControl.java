package mx.edu.itoaxaca.coordinadito.control;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import mx.edu.itoaxaca.coordinadito.control.vista.TramiteVista;
import modelo.bean.TramiteService;
import modelo.entity.Tramite;

/**
 * «control» Catálogo de trámites (vista/tramites.xhtml, HU 1.1 y 1.4).
 * Se carga una vez por vista: la búsqueda y el filtro (AJAX) trabajan sobre esta lista.
 */
@Named
@ViewScoped
public class CatalogoControl implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private TramiteService tramiteService;

    private List<TramiteVista> tramites;
    private String busqueda;
    private String filtro;

    @PostConstruct
    void cargar() {
        tramites = new ArrayList<>();
        for (Tramite t : tramiteService.catalogo()) {
            tramites.add(new TramiteVista(t));
        }
    }

    public List<TramiteVista> getTramites() {
        return tramites;
    }

    /** Trámites que cumplen la búsqueda (nombre o descripción) y el filtro «Disponibles». */
    public List<TramiteVista> getVisibles() {
        String q = busqueda == null ? "" : busqueda.trim().toLowerCase();
        List<TramiteVista> r = new ArrayList<>();
        for (TramiteVista t : tramites) {
            boolean pasaFiltro = !"disponibles".equals(filtro) || t.isDisponible();
            boolean pasaBusqueda = q.isEmpty() || t.getNombre().toLowerCase().contains(q)
                    || t.getDescripcion().toLowerCase().contains(q) || t.getCodigo().toLowerCase().contains(q);
            if (pasaFiltro && pasaBusqueda) {
                r.add(t);
            }
        }
        return r;
    }

    public String getBusqueda() { return busqueda; }
    public void setBusqueda(String busqueda) { this.busqueda = busqueda; }
    public String getFiltro() { return filtro; }
    public void setFiltro(String filtro) { this.filtro = filtro; }
}
