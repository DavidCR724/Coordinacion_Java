package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import mx.edu.itoaxaca.coordinadito.control.vista.GrupoVista;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.Opcion;
import modelo.bean.GrupoResumen;
import modelo.bean.GrupoService;
import modelo.entity.Materia;
import modelo.entity.Solicitud;

/**
 * «control» Apertura de grupos: la lista (vista/grupos.xhtml, HU 4.2 y 4.4) y la solicitud nueva
 * (vista/grupo-nuevo.xhtml, HU 4.1). Las dos pantallas leen la misma lista de aperturas: la nueva la
 * usa para avisar, en cuanto se elige la materia, que ya existe una y conviene adherirse.
 */
@Named
@ViewScoped
public class GruposControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private GrupoService grupoService;

    private List<GrupoVista> grupos;
    private List<Opcion> materias;

    // Solicitud nueva
    private String materia;
    private String motivo;
    private String folioNuevo;

    /** Aperturas visibles para quien mira, de la más demandada a la menos. */
    public List<GrupoVista> getGrupos() {
        if (grupos == null) {
            List<GrupoResumen> r = Mensajes.intentar(() -> grupoService.grupos(yo(), false, null));
            grupos = r == null ? Collections.<GrupoVista>emptyList()
                    : r.stream().map(GrupoVista::de).collect(Collectors.toList());
        }
        return grupos;
    }

    // ---------------- Lista ----------------

    /** Estudiante: las que inició o a las que se sumó. */
    public List<GrupoVista> getMias() {
        return filtrar(GrupoVista::isAdherido);
    }

    /** Estudiante: las demás activas de su carrera, para sumarse. */
    public List<GrupoVista> getOtrasActivas() {
        return filtrar(g -> !g.isAdherido() && g.isActiva());
    }

    public long getActivas() {
        return filtrar(GrupoVista::isActiva).size();
    }

    public long getInteresadosActivos() {
        return filtrar(GrupoVista::isActiva).stream().mapToLong(GrupoVista::getAdhesiones).sum();
    }

    public long getAbiertos() {
        return filtrar(g -> "completed".equals(g.getCodigoEstado())).size();
    }

    /** Opciones del filtro «Carrera» de la tabla de jefatura (solo las que aparecen). */
    public List<String> getClavesCarrera() {
        return new ArrayList<>(getGrupos().stream().map(GrupoVista::getClaveCarrera).collect(Collectors.toCollection(TreeSet::new)));
    }

    // ---------------- Solicitud nueva ----------------

    public List<Opcion> getMaterias() {
        if (materias == null) {
            List<Materia> r = Mensajes.intentar(() -> grupoService.materiasParaAlumno(yo()));
            materias = r == null ? Collections.<Opcion>emptyList()
                    : Opcion.de(r, Materia::getId, m -> m.getNombre() + " (" + m.getClave() + ") · " + m.getSemestre() + ".º semestre");
        }
        return materias;
    }

    /** La apertura activa de la materia elegida, si ya existe (null si no). */
    public GrupoVista getExistente() {
        if (materia == null) {
            return null;
        }
        List<GrupoVista> r = filtrar(g -> g.isActiva() && g.getIdMateria().equals(materia));
        return r.isEmpty() ? null : r.get(0);
    }

    public void registrar() {
        Solicitud s = Mensajes.intentar(() -> grupoService.solicitarApertura(yo(), UUID.fromString(materia), motivo));
        if (s == null) {
            grupos = null;   // si alguien la pidió primero, que aparezca como existente
            return;
        }
        folioNuevo = s.getFolioTexto();
        grupos = null;
    }

    // ---------------------------------------------------------------------

    private List<GrupoVista> filtrar(Predicate<GrupoVista> condicion) {
        return getGrupos().stream().filter(condicion).collect(Collectors.toList());
    }

    public String getMateria() { return materia; }
    public void setMateria(String materia) { this.materia = materia; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getFolioNuevo() { return folioNuevo; }
    public String getNumeroNuevo() { return folioNuevo == null ? null : String.valueOf(numeroDeFolio(folioNuevo)); }
}
