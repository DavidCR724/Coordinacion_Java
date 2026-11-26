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

/** «ORM persistable» Tabla CAMBIO_PRIORIDAD: elevación manual con motivo (HU 3.3). */
@Entity
@Table(name = "CAMBIO_PRIORIDAD")
public class CambioPrioridad implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_CAMBIO", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_SOLICITUD", nullable = false, updatable = false)
    private Solicitud solicitud;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRIORIDAD_ANTERIOR", nullable = false, updatable = false)
    private Prioridad anterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRIORIDAD_NUEVA", nullable = false, updatable = false)
    private Prioridad nueva;

    @Column(name = "MOTIVO", nullable = false, updatable = false)
    private String motivo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_ACTOR", nullable = false, updatable = false)
    private Usuario actor;

    @Column(name = "FECHA", nullable = false, updatable = false)
    private OffsetDateTime fecha = Fechas.ahora();

    protected CambioPrioridad() { }

    CambioPrioridad(Solicitud solicitud, Prioridad anterior, Prioridad nueva, Usuario actor, String motivo) {
        this.solicitud = solicitud;
        this.anterior = anterior;
        this.nueva = nueva;
        this.actor = actor;
        this.motivo = motivo;
    }

    public UUID getId() { return id; }
    public Solicitud getSolicitud() { return solicitud; }
    public Prioridad getAnterior() { return anterior; }
    public Prioridad getNueva() { return nueva; }
    public String getMotivo() { return motivo; }
    public Usuario getActor() { return actor; }
    public OffsetDateTime getFecha() { return fecha; }

    @Override public boolean equals(Object o) { return o instanceof CambioPrioridad && Objects.equals(id, ((CambioPrioridad) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
