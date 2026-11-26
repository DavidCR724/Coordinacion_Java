package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import modelo.entity.Notificacion;

/** Una notificación (tabla NOTIFICACION) como la pinta comp-fila-notificacion. */
public class AvisoVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String folio;
    private final String tramite;
    private final String titulo;
    private final String texto;
    private final String fecha;
    private final boolean sinLeer;
    private final String icono;
    private final String tono;

    public AvisoVista(Notificacion n) {
        folio = n.getSolicitud() == null ? "" : String.valueOf(n.getSolicitud().getFolio());
        tramite = n.getSolicitud() == null ? "" : n.getSolicitud().getTramite().getNombre();
        titulo = n.getTitulo();
        texto = n.getMensaje();
        fecha = Formato.fechaHora(n.getFecha());
        sinLeer = !n.isLeida();
        // El icono se deduce del título que escribe SolicitudService al avisar.
        String t = titulo == null ? "" : titulo.toLowerCase();
        if (t.contains("finalizada")) {
            icono = "check"; tono = "tono-verde";
        } else if (t.contains("rechazada")) {
            icono = "times"; tono = "tono-marino";
        } else if (t.startsWith("necesitamos")) {
            icono = "question-circle"; tono = "tono-naranja";
        } else if (t.startsWith("respuesta")) {
            icono = "reply"; tono = "tono-naranja";
        } else if (t.startsWith("recibimos") || t.startsWith("nueva solicitud")) {
            icono = "file"; tono = "";
        } else {
            icono = "clock"; tono = "";
        }
    }

    public String getFolio() { return folio; }
    public String getFolioTexto() { return folio.isEmpty() ? "" : String.format("ITO-%04d", Integer.parseInt(folio)); }
    public String getTramite() { return tramite; }
    public String getTitulo() { return titulo; }
    public String getTexto() { return texto; }
    public String getFecha() { return fecha; }
    /** Las notificaciones no guardan autor; se deja vacío para que la fila no lo muestre. */
    public String getActor() { return ""; }
    public boolean isSinLeer() { return sinLeer; }
    public String getIcono() { return icono; }
    public String getTono() { return tono; }
}
