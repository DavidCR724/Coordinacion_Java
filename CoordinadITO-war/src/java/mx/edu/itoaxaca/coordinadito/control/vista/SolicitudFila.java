package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import modelo.entity.Solicitud;
import modelo.entity.Usuario;

/** Una fila de comp-tabla-solicitudes (mis solicitudes, bandeja, inicio). */
public class SolicitudFila implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String folio;
    private final String tramite;
    private final String estudiante;
    private final String control;
    private final String carrera;
    private final String codigoEstado;
    private final String prioridad;
    private final int ordenPrioridad;
    private final String registro;
    private final String registroIso;
    private final String actualizacion;
    private final String actualizacionIso;

    public SolicitudFila(Solicitud s) {
        Usuario a = s.getSolicitante();
        folio = String.valueOf(s.getFolio());
        tramite = s.getTramite().getNombre();
        estudiante = a.getNombreCompleto();
        control = a.getNumControl() == null ? "" : a.getNumControl();
        carrera = s.getCarrera() == null ? "" : s.getCarrera().getNombre();
        codigoEstado = Formato.codigo(s.getEstado());
        prioridad = s.getPrioridad().name();
        ordenPrioridad = s.getPrioridad().ordinal();
        registro = Formato.fecha(s.getFechaRegistro());
        registroIso = Formato.iso(s.getFechaRegistro());
        actualizacion = Formato.fecha(s.getFechaActualizacion());
        actualizacionIso = Formato.iso(s.getFechaActualizacion());
    }

    /** Número del folio (sin «ITO-»); las vistas lo muestran como ITO-#{folio}. */
    public String getFolio() { return folio; }
    public String getFolioTexto() { return String.format("ITO-%04d", Integer.parseInt(folio)); }
    public String getTramite() { return tramite; }
    public String getEstudiante() { return estudiante; }
    public String getControl() { return control; }
    public String getCarrera() { return carrera; }
    public String getCodigoEstado() { return codigoEstado; }
    /** Recibida, en revisión o esperando información: todavía se está atendiendo. */
    public boolean isActiva() {
        return "received".equals(codigoEstado) || "in-review".equals(codigoEstado) || "waiting".equals(codigoEstado);
    }
    public String getPrioridad() { return prioridad; }
    public int getOrdenPrioridad() { return ordenPrioridad; }
    public String getRegistro() { return registro; }
    public String getRegistroIso() { return registroIso; }
    public String getActualizacion() { return actualizacion; }
    public String getActualizacionIso() { return actualizacionIso; }
}
