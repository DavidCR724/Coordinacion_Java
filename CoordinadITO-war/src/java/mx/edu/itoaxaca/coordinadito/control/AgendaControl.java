package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import org.primefaces.event.ToggleEvent;
import org.primefaces.model.Visibility;
import jakarta.inject.Named;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import mx.edu.itoaxaca.coordinadito.control.vista.EspacioVista;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import modelo.bean.AgendaService;
import modelo.entity.Fechas;

/**
 * «control» Agenda de atención presencial de coordinación (vista/agenda.xhtml, HU 9.1 y 9.3).
 * Publica bloques de un día, ve los próximos 14 días (libres y ocupados), cancela bloques y
 * registra la asistencia de las citas del día elegido.
 */
@Named
@ViewScoped
public class AgendaControl extends ControlBase {

    private static final long serialVersionUID = 1L;
    private static final int DIAS_VISIBLES = 14;

    @EJB
    private AgendaService agendaService;

    // Publicar
    private LocalDate dia = Fechas.hoy().plusDays(1);
    private LocalTime desde = LocalTime.of(10, 0);
    private LocalTime hasta = LocalTime.of(13, 0);
    private Integer minutos = 20;
    private String lugar;

    /** Día de la lista de citas (asistencia). */
    private LocalDate diaCitas = Fechas.hoy();

    private List<EspacioVista> bloques;
    /** ¿Está abierto «Publicar horario»? Al entrar, solo si aún no hay horarios; luego, lo que elija. */
    private Boolean publicarAbierto;
    private List<EspacioVista> citas;

    public void publicar() {
        Integer n = Mensajes.intentar(() -> agendaService.publicarBloques(yo(), dia, desde, hasta, minutos, lugar));
        if (n != null) {
            Mensajes.info("Horario publicado", n == 1 ? "Se creó 1 bloque." : "Se crearon " + n + " bloques.");
            olvidar();
        }
    }

    public void cancelarBloque(String idBloque) {
        if (hecho(() -> agendaService.cancelarBloque(yo(), UUID.fromString(idBloque)), "Bloque cancelado",
                "Si tenía cita, el alumno recibió un aviso para elegir otro horario.")) {
            olvidar();
        }
    }

    public void asistencia(String idCita, boolean asistio) {
        if (hecho(() -> agendaService.registrarAsistencia(yo(), UUID.fromString(idCita), asistio),
                asistio ? "Asistencia registrada" : "Inasistencia registrada",
                asistio ? "La solicitud ya no requiere cita." : "El alumno puede agendar otra.")) {
            olvidar();
        }
    }

    /** Al cambiar el día de citas. */
    public void olvidar() {
        bloques = null;
        citas = null;
    }

    public boolean isPublicarAbierto() {
        if (publicarAbierto == null) {
            publicarAbierto = getBloques().isEmpty();
        }
        return publicarAbierto;
    }

    /** El panel avisa cuando se abre o se cierra, para que no se pliegue solo al redibujarse. */
    public void alternarPublicar(ToggleEvent e) {
        publicarAbierto = e.getVisibility() == Visibility.VISIBLE;
    }

    public List<EspacioVista> getBloques() {
        if (bloques == null) {
            LocalDate hoy = Fechas.hoy();
            bloques = convertir(() -> agendaService.misBloques(yo(), hoy, hoy.plusDays(DIAS_VISIBLES)).stream()
                    .map(e -> new EspacioVista(e.getBloque(), e.getCita())).collect(Collectors.toList()));
        }
        return bloques;
    }

    public List<EspacioVista> getCitas() {
        if (citas == null) {
            citas = convertir(() -> agendaService.agendaDelDia(yo(), diaCitas).stream()
                    .map(EspacioVista::new).collect(Collectors.toList()));
        }
        return citas;
    }

    public long getOcupados() {
        return getBloques().stream().filter(b -> !b.isLibre()).count();
    }

    private static List<EspacioVista> convertir(Supplier<List<EspacioVista>> consulta) {
        List<EspacioVista> r = Mensajes.intentar(consulta);
        return r == null ? Collections.<EspacioVista>emptyList() : r;
    }

    public LocalDate getHoy() { return Fechas.hoy(); }
    public LocalDate getDia() { return dia; }
    public void setDia(LocalDate dia) { this.dia = dia; }
    public LocalTime getDesde() { return desde; }
    public void setDesde(LocalTime desde) { this.desde = desde; }
    public LocalTime getHasta() { return hasta; }
    public void setHasta(LocalTime hasta) { this.hasta = hasta; }
    public Integer getMinutos() { return minutos; }
    public void setMinutos(Integer minutos) { this.minutos = minutos; }
    public String getLugar() { return lugar; }
    public void setLugar(String lugar) { this.lugar = lugar; }
    public LocalDate getDiaCitas() { return diaCitas; }
    public void setDiaCitas(LocalDate diaCitas) { this.diaCitas = diaCitas; }
}
