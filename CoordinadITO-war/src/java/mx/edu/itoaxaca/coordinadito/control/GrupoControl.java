package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import mx.edu.itoaxaca.coordinadito.control.vista.GrupoVista;
import mx.edu.itoaxaca.coordinadito.control.vista.PersonaFila;
import mx.edu.itoaxaca.coordinadito.control.vista.SolicitudDetalle;
import modelo.bean.AccesoDenegadoException;
import modelo.bean.GrupoService;
import modelo.bean.NoEncontradoException;
import modelo.bean.SolicitudService;
import modelo.entity.Adhesion;
import modelo.entity.Solicitud;

/**
 * «control» Detalle de una apertura de grupo (vista/grupo-detalle.xhtml, HU 4.2 – 4.4).
 * El alumno se adhiere o se retira; coordinación y jefatura ven la lista de interesados.
 * La decisión (abrir o no el grupo) se toma en el detalle de la solicitud, con el mismo flujo de
 * estados y constancia que cualquier trámite: no hay una segunda manera de atender.
 */
@Named
@ViewScoped
public class GrupoControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB private SolicitudService solicitudService;
    @EJB private GrupoService grupoService;

    /** ?id= (número de folio) */
    private String id;
    private GrupoVista g;
    private SolicitudDetalle sol;
    private String promotor;
    private String motivo;
    private final List<PersonaFila> alumnos = new ArrayList<>();

    /** f:viewAction */
    public void cargar() {
        if (g == null) {
            recargar();
        }
    }

    private void recargar() {
        g = null;
        alumnos.clear();
        Integer folio = numeroDeFolio(id);
        if (folio == null) {
            return;
        }
        try {
            Solicitud s = solicitudService.detalle(folio, yo());
            if (!s.isColectiva()) {
                return;
            }
            g = GrupoVista.de(s, yo());
            sol = new SolicitudDetalle(s);
            promotor = s.getSolicitante().getNombreCompleto();
            motivo = s.getPeticion() == null || s.getPeticion().isEmpty() ? "Sin motivo escrito." : s.getPeticion();
            alumnos.add(new PersonaFila(s.getSolicitante(), s.getFechaRegistro()));
            for (Adhesion a : s.getAdhesiones()) {
                alumnos.add(new PersonaFila(a.getAlumno(), a.getFecha()));
            }
        } catch (AccesoDenegadoException | NoEncontradoException e) {
            g = null;
        }
    }

    public void adherirse() {
        if (hecho(() -> grupoService.adherirse(yo(), numero()), "Te sumaste",
                "Ya cuentas en esta solicitud y recibirás sus avisos.")) {
            recargar();
        }
    }

    public void retirarse() {
        if (hecho(() -> grupoService.retirarse(yo(), numero()), "Adhesión retirada",
                "Ya no cuentas en esta solicitud ni recibirás sus avisos.")) {
            recargar();
        }
    }

    private int numero() {
        return Integer.parseInt(g.getId());
    }

    public boolean isVisible() { return g != null; }
    /** El alumno puede sumarse o retirarse mientras siga activa (quien la inició no se retira). */
    public boolean isPuedeAdherirse() { return g != null && esRol("student") && g.isActiva() && !g.isAdherido(); }
    public boolean isPuedeRetirarse() { return g != null && esRol("student") && g.isActiva() && g.isAdherido() && !g.isPropia(); }
    public boolean isPuedeAtender() { return g != null && esRol("coordinator") && g.isActiva(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public GrupoVista getG() { return g; }
    public SolicitudDetalle getSol() { return sol; }
    public String getPromotor() { return promotor; }
    public String getMotivo() { return motivo; }
    public List<PersonaFila> getAlumnos() { return alumnos; }
}
