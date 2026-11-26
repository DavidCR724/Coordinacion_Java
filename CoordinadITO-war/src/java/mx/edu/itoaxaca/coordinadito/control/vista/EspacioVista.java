package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.time.ZonedDateTime;
import modelo.entity.BloqueAtencion;
import modelo.entity.Cita;
import modelo.entity.EstadoCita;
import modelo.entity.Fechas;

/**
 * Un bloque de atención presencial y, si lo hay, la cita que lo ocupa (HU 9.1 – 9.3).
 * Sirve igual para la agenda de coordinación, los horarios libres que ve el alumno y su cita.
 */
public class EspacioVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String idBloque;
    private final String dia;
    private final String diaIso;
    private final String hora;
    private final String lugar;
    private final String atiende;
    private final boolean futuro;
    private final String idCita;
    private final String alumno;
    private final String control;
    private final String folio;
    private final String folioTexto;
    private final String estado;
    private final String etiquetaEstado;

    public EspacioVista(BloqueAtencion b, Cita c) {
        ZonedDateTime inicio = b.getInicio().atZoneSameInstant(Fechas.ZONA);
        ZonedDateTime fin = b.getFin().atZoneSameInstant(Fechas.ZONA);
        idBloque = b.getId().toString();
        dia = Formato.fecha(inicio.toLocalDate());
        diaIso = inicio.toLocalDate().toString();
        hora = hhmm(inicio) + " – " + hhmm(fin);
        lugar = b.getLugar();
        atiende = b.getCoordinador().getNombreCompleto();
        futuro = b.isFuturo();
        idCita = c == null ? null : c.getId().toString();
        alumno = c == null ? null : c.getAlumno().getNombreCompleto();
        control = c == null ? null : c.getAlumno().getNumControl();
        folio = c == null ? null : String.valueOf(c.getSolicitud().getFolio());
        folioTexto = c == null ? null : c.getSolicitud().getFolioTexto();
        estado = c == null ? null : c.getEstado().name();
        etiquetaEstado = c == null ? "Libre" : c.getEstado().getEtiqueta();
    }

    /** La cita con su bloque. */
    public EspacioVista(Cita c) {
        this(c.getBloque(), c);
    }

    private static String hhmm(ZonedDateTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    public String getIdBloque() { return idBloque; }
    public String getDia() { return dia; }
    public String getDiaIso() { return diaIso; }
    public String getHora() { return hora; }
    /** «27 oct 2026, 10:00 – 10:20» */
    public String getCuando() { return dia + ", " + hora; }
    public String getLugar() { return lugar; }
    public String getAtiende() { return atiende; }
    public boolean isFuturo() { return futuro; }
    public boolean isLibre() { return idCita == null; }
    public String getIdCita() { return idCita; }
    public String getAlumno() { return alumno; }
    public String getControl() { return control; }
    public String getFolio() { return folio; }
    public String getFolioTexto() { return folioTexto; }
    public boolean isProgramada() { return EstadoCita.PROGRAMADA.name().equals(estado); }
    public String getEstado() { return estado; }
    public String getEtiquetaEstado() { return etiquetaEstado; }
}
