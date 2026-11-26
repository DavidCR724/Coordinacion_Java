package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.edu.itoaxaca.coordinadito.control.vista.EspacioVista;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.SolicitudDetalle;
import modelo.bean.AgendaService;
import modelo.bean.SolicitudService;
import modelo.entity.BloqueAtencion;
import modelo.entity.Cita;
import org.primefaces.PrimeFaces;

/**
 * «control» Panel de cita presencial dentro del detalle de una solicitud (HU 9.2).
 *     Coordinación → «Pedir cita presencial» con el motivo.
 *     Estudiante   → elige un horario libre, lo cambia o cancela (hasta 12 h antes).
 *     Todos        → ven la cita vigente.
 * Trabaja sobre la solicitud que ya cargó {@link DetalleSolicitudControl} (misma vista) y le pide
 * recargarla después de cada cambio, para que bitácora y mensajes estén al día.
 */
@Named
@ViewScoped
public class CitaControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB private AgendaService agendaService;
    @EJB private SolicitudService solicitudService;
    private Boolean sinHorarios;
    @Inject private DetalleSolicitudControl detalle;

    private String motivo;
    private EspacioVista cita;
    private List<EspacioVista> horarios;
    private boolean leida;

    public EspacioVista getCita() {
        if (!leida && sol() != null) {
            Optional<Cita> c = Mensajes.intentar(() -> agendaService.citaVigente(yo(), folio()));
            cita = c == null ? null : c.map(EspacioVista::new).orElse(null);
            leida = true;
        }
        return cita;
    }

    /** Horarios libres de la coordinación de su carrera (solo el alumno, cuando se le pidió cita). */
    public List<EspacioVista> getHorarios() {
        if (horarios == null) {
            List<BloqueAtencion> r = isPuedeAgendar() ? Mensajes.intentar(() -> agendaService.horariosLibres(yo(), folio())) : null;
            horarios = r == null ? Collections.<EspacioVista>emptyList()
                    : r.stream().map(b -> new EspacioVista(b, null)).collect(Collectors.toList());
        }
        return horarios;
    }

    public void pedir() {
        if (hecho(() -> solicitudService.requerirCita(folio(), motivo, yo()), "Cita solicitada",
                "El alumno recibió el aviso para elegir un horario.")) {
            motivo = null;
            refrescar();
            PrimeFaces.current().ajax().update("detalle");   // el botón solo actualiza su formulario
        }
    }

    public void agendar(String idBloque) {
        if (hecho(() -> agendaService.agendar(yo(), folio(), UUID.fromString(idBloque)), "Cita agendada",
                "Te esperamos en el horario elegido. Puedes cambiarlo hasta 12 horas antes.")) {
            refrescar();
        }
    }

    public void cancelar() {
        if (hecho(() -> agendaService.cancelarCita(yo(), UUID.fromString(getCita().getIdCita())), "Cita cancelada",
                "Puedes elegir otro horario cuando quieras.")) {
            refrescar();
        }
    }

    private void refrescar() {
        leida = false;
        horarios = null;
        detalle.recargar();
    }

    private SolicitudDetalle sol() {
        return detalle.isVisible() ? detalle.getSol() : null;
    }

    private int folio() {
        return Integer.parseInt(sol().getFolio());
    }

    /** Coordinación puede pedir la cita en una solicitud abierta que aún no la pide. */
    public boolean isPuedePedir() {
        return detalle.isPuedeAtender() && !sol().isCitaRequerida();
    }

    /** El alumno dueño agenda si se le pidió cita y la solicitud sigue abierta. */
    public boolean isPuedeAgendar() {
        SolicitudDetalle s = sol();
        return s != null && esRol("student") && s.isCitaRequerida() && !s.isCerrada();
    }

    /**
     * ¿La coordinación que pide la cita tiene horarios libres en los próximos 14 días? Si no, el
     * alumno no tendrá qué elegir: se le avisa al pedirla para que publique en Agenda.
     */
    public boolean isSinHorariosLibres() {
        if (sinHorarios == null) {
            List<?> libres = Mensajes.intentar(() -> agendaService.misBloques(yo(), modelo.entity.Fechas.hoy(),
                    modelo.entity.Fechas.hoy().plusDays(14)).stream().filter(e -> e.getCita() == null).collect(Collectors.toList()));
            sinHorarios = libres != null && libres.isEmpty();
        }
        return sinHorarios;
    }

    public boolean isVisible() {
        SolicitudDetalle s = sol();
        return s != null && (s.isCitaRequerida() || isPuedePedir() || getCita() != null);
    }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
