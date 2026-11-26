package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import modelo.entity.MensajeSolicitud;
import modelo.entity.TipoMensaje;

/** Un mensaje dentro de la solicitud: petición de información o respuesta del alumno (HU 2.5, 2.6). */
public class MensajeVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String tipo;
    private final String titulo;
    private final String autor;
    private final String contenido;
    private final String fecha;

    public MensajeVista(MensajeSolicitud m) {
        id = m.getId().toString();
        tipo = m.getTipo().name();
        titulo = m.getTipo() == TipoMensaje.SOLICITUD_INFORMACION ? "Coordinación pidió información"
                : (m.getTipo() == TipoMensaje.RESPUESTA ? "Respuesta del alumno" : "Nota");
        autor = m.getAutor() == null ? "" : m.getAutor().getNombreCompleto();
        contenido = m.getContenido();
        fecha = Formato.fechaHora(m.getFecha());
    }

    public String getId() { return id; }
    /** SOLICITUD_INFORMACION, RESPUESTA o NOTA. */
    public String getTipo() { return tipo; }
    public boolean isPeticion() { return "SOLICITUD_INFORMACION".equals(tipo); }
    public String getTitulo() { return titulo; }
    public String getAutor() { return autor; }
    public String getContenido() { return contenido; }
    public String getFecha() { return fecha; }
}
