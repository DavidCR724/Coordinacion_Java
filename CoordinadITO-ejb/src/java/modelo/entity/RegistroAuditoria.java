package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * «ORM persistable» Tabla AUDITORIA (HU 8.2). Solo se inserta: un disparador de la base
 * rechaza cualquier UPDATE o DELETE, y esta clase no tiene setters.
 */
@Entity
@Table(name = "AUDITORIA")
public class RegistroAuditoria implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_AUDITORIA", updatable = false)
    private Long id;

    /** Null si fue un intento fallido de alguien sin cuenta, o un proceso del sistema. */
    @ManyToOne
    @JoinColumn(name = "ID_ACTOR", updatable = false)
    private Usuario actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACCION", nullable = false, updatable = false)
    private Accion accion;

    @Column(name = "ENTIDAD", nullable = false, updatable = false)
    private String entidad;

    @Column(name = "ID_ENTIDAD", updatable = false)
    private String idEntidad;

    /** JSON con los valores antes y después (columna JSONB). */
    @Column(name = "DETALLE", updatable = false)
    private String detalle;

    /** Columna INET. */
    @Column(name = "IP", updatable = false)
    private String ip;

    @Column(name = "FECHA", nullable = false, updatable = false)
    private OffsetDateTime fecha = Fechas.ahora();

    protected RegistroAuditoria() { }

    public RegistroAuditoria(Usuario actor, Accion accion, String entidad, String idEntidad, String detalle, String ip) {
        this.actor = actor;
        this.accion = accion;
        this.entidad = entidad;
        this.idEntidad = idEntidad;
        this.detalle = detalle;
        this.ip = ip;
    }

    public Long getId() { return id; }
    public Usuario getActor() { return actor; }
    public Accion getAccion() { return accion; }
    public String getEntidad() { return entidad; }
    public String getIdEntidad() { return idEntidad; }
    public String getDetalle() { return detalle; }
    public String getIp() { return ip; }
    public OffsetDateTime getFecha() { return fecha; }
}
