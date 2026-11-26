package modelo.bean;

import java.io.Serializable;

/** Archivo que sube el usuario (lo arma el control a partir del componente de carga de PrimeFaces). */
public class ArchivoNuevo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final String mime;
    private final byte[] contenido;

    public ArchivoNuevo(String nombre, String mime, byte[] contenido) {
        this.nombre = nombre;
        this.mime = mime;
        this.contenido = contenido == null ? new byte[0] : contenido;
    }

    public String getNombre() { return nombre; }
    public String getMime() { return mime; }
    public byte[] getContenido() { return contenido; }
    public long getTamano() { return contenido.length; }
}
