package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.Opcion;
import modelo.bean.CampoDatos;
import modelo.bean.TramiteDatos;
import modelo.bean.TramiteService;
import modelo.entity.TipoCampo;
import modelo.entity.Tramite;

/**
 * «control» Alta y edición de un trámite con su formulario (vista/tramite-editar.xhtml, HU 8.3).
 * La pantalla edita directamente un {@link TramiteDatos}; los campos se agregan, quitan y
 * reordenan en memoria y todo se guarda junto (y se valida) en TramiteService.guardar.
 */
@Named
@ViewScoped
public class TramiteEdicionControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private TramiteService tramiteService;

    /** ?id= (vacío = trámite nuevo) */
    private String id;
    private TramiteDatos datos;

    /** f:viewAction */
    public void cargar() {
        if (datos != null) {
            return;
        }
        if (id == null || id.isEmpty()) {
            datos = new TramiteDatos();
            return;
        }
        try {
            UUID uuid = UUID.fromString(id);
            datos = Mensajes.intentar(() -> tramiteService.paraEditar(uuid, yo()));
        } catch (IllegalArgumentException e) {
            datos = null;
        }
    }

    public void agregarCampo() {
        datos.getCampos().add(new CampoDatos());
    }

    public void quitarCampo(int i) {
        datos.getCampos().remove(i);
    }

    /** Sube (−1) o baja (+1) un campo en el formulario. */
    public void mover(int i, int direccion) {
        int j = i + direccion;
        if (j >= 0 && j < datos.getCampos().size()) {
            Collections.swap(datos.getCampos(), i, j);
        }
    }

    public String guardar() {
        Tramite t = Mensajes.intentar(() -> tramiteService.guardar(datos, yo()));
        if (t == null) {
            return null;
        }
        Mensajes.info("Trámite guardado", "«" + t.getNombre() + "» quedó actualizado en el catálogo.");
        return Mensajes.redirigir("/vista/catalogo-tramites");
    }

    public List<Opcion> getTipos() {
        return Opcion.de(Arrays.asList(TipoCampo.values()), TipoCampo::name, TipoCampo::getEtiqueta);
    }

    public boolean isVisible() { return datos != null; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public TramiteDatos getDatos() { return datos; }
}
