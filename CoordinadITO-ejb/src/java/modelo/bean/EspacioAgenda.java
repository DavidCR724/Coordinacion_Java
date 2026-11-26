package modelo.bean;

import java.io.Serializable;
import modelo.entity.BloqueAtencion;
import modelo.entity.Cita;

/** Un bloque de atención y, si alguien lo ocupó, su cita (HU 9.1, 9.3). */
public class EspacioAgenda implements Serializable {

    private static final long serialVersionUID = 1L;

    private final BloqueAtencion bloque;
    private final Cita cita;

    public EspacioAgenda(BloqueAtencion bloque, Cita cita) {
        this.bloque = bloque;
        this.cita = cita;
    }

    public BloqueAtencion getBloque() { return bloque; }
    /** Null si el bloque sigue libre. */
    public Cita getCita() { return cita; }
    public boolean isLibre() { return cita == null; }
}
