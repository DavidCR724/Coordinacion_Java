package modelo.bean;

import java.io.Serializable;
import modelo.entity.Usuario;

/**
 * Qué sigue después de autenticarse (HU 6.1, 6.4):
 * ADMITIDO     → ya tiene cuenta; entra.
 * PRIMER_ACCESO → es estudiante del ITO pero no está en el padrón: debe declarar carrera y semestre.
 */
public final class ResultadoAcceso implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Usuario usuario;
    private final Identidad identidad;

    private ResultadoAcceso(Usuario usuario, Identidad identidad) {
        this.usuario = usuario;
        this.identidad = identidad;
    }

    static ResultadoAcceso admitido(Usuario u) {
        return new ResultadoAcceso(u, null);
    }

    static ResultadoAcceso primerAcceso(Identidad identidad) {
        return new ResultadoAcceso(null, identidad);
    }

    public boolean isAdmitido() { return usuario != null; }
    public boolean isPrimerAcceso() { return usuario == null; }
    public Usuario getUsuario() { return usuario; }
    public Identidad getIdentidad() { return identidad; }
}
