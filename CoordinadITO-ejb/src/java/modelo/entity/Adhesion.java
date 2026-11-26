package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/** «ORM persistable» Tabla ADHESION: un alumno que se suma a una solicitud de apertura de grupo (HU 4.2). */
@Entity
@Table(name = "ADHESION")
@IdClass(AdhesionId.class)
public class Adhesion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_SOLICITUD", nullable = false, updatable = false)
    private Solicitud solicitud;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_ALUMNO", nullable = false, updatable = false)
    private Usuario alumno;

    @Column(name = "FECHA", nullable = false, updatable = false)
    private OffsetDateTime fecha = Fechas.ahora();

    protected Adhesion() { }

    Adhesion(Solicitud solicitud, Usuario alumno) {
        this.solicitud = solicitud;
        this.alumno = alumno;
    }

    public Solicitud getSolicitud() { return solicitud; }
    public Usuario getAlumno() { return alumno; }
    public OffsetDateTime getFecha() { return fecha; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Adhesion && Objects.equals(solicitud, ((Adhesion) o).solicitud)
                && Objects.equals(alumno, ((Adhesion) o).alumno);
    }

    @Override
    public int hashCode() {
        return Objects.hash(solicitud, alumno);
    }
}
