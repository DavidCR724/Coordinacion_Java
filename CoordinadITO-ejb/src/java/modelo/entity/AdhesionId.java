package modelo.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Llave compuesta de ADHESION (solicitud + alumno). */
public class AdhesionId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID solicitud;
    private UUID alumno;

    public AdhesionId() { }

    public AdhesionId(UUID solicitud, UUID alumno) {
        this.solicitud = solicitud;
        this.alumno = alumno;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AdhesionId)) {
            return false;
        }
        AdhesionId x = (AdhesionId) o;
        return Objects.equals(solicitud, x.solicitud) && Objects.equals(alumno, x.alumno);
    }

    @Override
    public int hashCode() {
        return Objects.hash(solicitud, alumno);
    }
}
