package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** «ORM persistable» Tabla CITA: el alumno ocupa un bloque para atender su solicitud en persona (HU 9.2, 9.3). */
@Entity
@Table(name = "CITA")
public class Cita implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_CITA", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_BLOQUE", nullable = false, updatable = false)
    private BloqueAtencion bloque;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_SOLICITUD", nullable = false, updatable = false)
    private Solicitud solicitud;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_ALUMNO", nullable = false, updatable = false)
    private Usuario alumno;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoCita estado = EstadoCita.PROGRAMADA;

    @Column(name = "FECHA_REGISTRO", nullable = false, updatable = false)
    private OffsetDateTime fechaRegistro = Fechas.ahora();

    protected Cita() { }

    public Cita(BloqueAtencion bloque, Solicitud solicitud, Usuario alumno) {
        this.bloque = bloque;
        this.solicitud = solicitud;
        this.alumno = alumno;
    }

    public boolean isProgramada() {
        return estado == EstadoCita.PROGRAMADA;
    }

    /** Solo una cita programada cambia de estado (cancelar, asistió, no asistió). */
    public void cerrar(EstadoCita nuevo) {
        if (!isProgramada()) {
            throw new IllegalStateException("La cita ya no está programada.");
        }
        estado = nuevo;
    }

    public UUID getId() { return id; }
    public BloqueAtencion getBloque() { return bloque; }
    public Solicitud getSolicitud() { return solicitud; }
    public Usuario getAlumno() { return alumno; }
    public EstadoCita getEstado() { return estado; }
    public OffsetDateTime getFechaRegistro() { return fechaRegistro; }

    @Override public boolean equals(Object o) { return o instanceof Cita && Objects.equals(id, ((Cita) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
