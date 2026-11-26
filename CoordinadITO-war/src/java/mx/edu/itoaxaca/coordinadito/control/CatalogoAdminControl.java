package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.edu.itoaxaca.coordinadito.control.vista.TramiteVista;
import modelo.bean.TramiteService;
import modelo.entity.Tramite;

/**
 * «control» Catálogo de trámites para jefatura (vista/catalogo-tramites.xhtml, HU 8.3):
 * todos los trámites, con sus solicitudes abiertas, para crear, editar o eliminar.
 * Un trámite con solicitudes no se elimina: se desactiva (lo decide TramiteService).
 */
@Named
@ViewScoped
public class CatalogoAdminControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private TramiteService tramiteService;

    private List<TramiteVista> tramites;

    public List<TramiteVista> getTramites() {
        if (tramites == null) {
            Map<UUID, Long> abiertas = tramiteService.abiertasPorTramite();
            tramites = new ArrayList<>();
            for (Tramite t : tramiteService.todos()) {
                TramiteVista v = new TramiteVista(t);
                v.setAbiertas(abiertas.getOrDefault(t.getId(), 0L));
                tramites.add(v);
            }
        }
        return tramites;
    }

    public void eliminar(TramiteVista t) {
        if (hecho(() -> tramiteService.eliminar(UUID.fromString(t.getId()), yo()), "Trámite eliminado",
                "«" + t.getNombre() + "» ya no aparece en el catálogo.")) {
            tramites = null;
        }
    }
}
