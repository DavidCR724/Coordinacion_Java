package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import modelo.entity.DocumentoAdjunto;

/** Un documento adjunto, para comp-adjuntos. */
public class AdjuntoVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String nombre;
    private final String mime;
    private final String tamano;
    private final String fecha;
    private final String autor;
    private final String idMensaje;

    public AdjuntoVista(DocumentoAdjunto d) {
        id = d.getId().toString();
        nombre = d.getNombreOriginal();
        mime = d.getMime();
        tamano = Formato.tamano(d.getTamanoBytes());
        fecha = Formato.fechaHora(d.getFechaSubida());
        autor = d.getSubidoPor() == null ? "" : d.getSubidoPor().getNombreCompleto();
        idMensaje = d.getMensaje() == null ? null : d.getMensaje().getId().toString();
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getMime() { return mime; }
    public String getTamano() { return tamano; }
    public String getFecha() { return fecha; }
    public String getAutor() { return autor; }
    /** Mensaje al que pertenece (respuesta del alumno) o null si se subió al registrar. */
    public String getIdMensaje() { return idMensaje; }
    public String getIcono() {
        return mime != null && mime.startsWith("image/") ? "image" : "file-pdf";
    }
}
