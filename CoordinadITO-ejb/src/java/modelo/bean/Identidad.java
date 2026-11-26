package modelo.bean;

import java.io.Serializable;
import java.util.Locale;

/**
 * Quién es la persona según el proveedor de identidad institucional (HU 6.1).
 * Llega al EJB ya verificada (firma, emisor, audiencia y vigencia del token revisados en el WAR):
 * aquí solo se decide qué cuenta le corresponde.
 */
public class Identidad implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String correo;
    private final String nombres;
    private final String apellidos;

    public Identidad(String correo, String nombres, String apellidos) {
        this.correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
        this.nombres = Reglas.limpio(nombres);
        this.apellidos = Reglas.limpio(apellidos);
    }

    public String getCorreo() { return correo; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }

    /** Parte antes de la @. */
    public String getUsuarioCorreo() {
        int i = correo == null ? -1 : correo.indexOf('@');
        return i < 0 ? correo : correo.substring(0, i);
    }

    public String getDominio() {
        int i = correo == null ? -1 : correo.indexOf('@');
        return i < 0 ? "" : correo.substring(i + 1);
    }

    /** Primer apellido (los apellidos llegan juntos en el token: «Cervantes Medina»). */
    public String getApellidoPaterno() {
        return apellidos == null ? null : apellidos.split("\\s+", 2)[0];
    }

    public String getApellidoMaterno() {
        String[] partes = apellidos == null ? new String[0] : apellidos.split("\\s+", 2);
        return partes.length > 1 ? partes[1] : null;
    }
}
