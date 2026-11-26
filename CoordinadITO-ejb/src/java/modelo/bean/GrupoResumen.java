package modelo.bean;

import java.io.Serializable;
import modelo.entity.Solicitud;

/** Una solicitud de apertura con su demanda y la relación de quien la mira (HU 4.2 a 4.4). */
public class GrupoResumen implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Solicitud solicitud;
    private final long interesados;
    private final boolean propia;
    private final boolean adherido;

    public GrupoResumen(Solicitud solicitud, long interesados, boolean propia, boolean adherido) {
        this.solicitud = solicitud;
        this.interesados = interesados;
        this.propia = propia;
        this.adherido = adherido;
    }

    public Solicitud getSolicitud() { return solicitud; }
    /** Quien la pidió más los adheridos. */
    public long getInteresados() { return interesados; }
    /** La pidió quien la mira. */
    public boolean isPropia() { return propia; }
    /** Quien la mira se adhirió. */
    public boolean isAdherido() { return adherido; }
}
