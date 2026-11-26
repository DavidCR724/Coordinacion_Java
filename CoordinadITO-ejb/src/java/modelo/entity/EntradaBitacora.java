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

/** «ORM persistable» Tabla HISTORIAL_SOLICITUD: un cambio de estado con su constancia (HU 2.4, 2.8). */
@Entity
@Table(name = "HISTORIAL_SOLICITUD")
public class EntradaBitacora implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_HISTORIAL_SOLICITUD", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_SOLICITUD", nullable = false, updatable = false)
    private Solicitud solicitud;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_ANTERIOR", updatable = false)
    private EstadoSolicitud estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_NUEVO", nullable = false, updatable = false)
    private EstadoSolicitud estadoNuevo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_ACTOR", nullable = false, updatable = false)
    private Usuario actor;

    @Column(name = "COMENTARIO", updatable = false)
    private String comentario;

    @Column(name = "FECHA", nullable = false, updatable = false)
    private OffsetDateTime fecha = Fechas.ahora();

    protected EntradaBitacora() { }

    EntradaBitacora(Solicitud solicitud, EstadoSolicitud anterior, EstadoSolicitud nuevo, Usuario actor, String comentario) {
        this.solicitud = solicitud;
        this.estadoAnterior = anterior;
        this.estadoNuevo = nuevo;
        this.actor = actor;
        this.comentario = comentario;
    }

    public UUID getId() { return id; }
    public Solicitud getSolicitud() { return solicitud; }
    public EstadoSolicitud getEstadoAnterior() { return estadoAnterior; }
    public EstadoSolicitud getEstadoNuevo() { return estadoNuevo; }
    public Usuario getActor() { return actor; }
    public String getComentario() { return comentario; }
    public OffsetDateTime getFecha() { return fecha; }

    @Override public boolean equals(Object o) { return o instanceof EntradaBitacora && Objects.equals(id, ((EntradaBitacora) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
