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

/** «ORM persistable» Tabla MENSAJE_SOLICITUD: conversación dentro de una solicitud (HU 2.5, 2.6). */
@Entity
@Table(name = "MENSAJE_SOLICITUD")
public class MensajeSolicitud implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_MENSAJE", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_SOLICITUD", nullable = false, updatable = false)
    private Solicitud solicitud;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_AUTOR", nullable = false, updatable = false)
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, updatable = false)
    private TipoMensaje tipo;

    @Column(name = "CONTENIDO", nullable = false, updatable = false)
    private String contenido;

    @ManyToOne
    @JoinColumn(name = "ID_RESPONDE_A", updatable = false)
    private MensajeSolicitud respondeA;

    @Column(name = "FECHA", nullable = false, updatable = false)
    private OffsetDateTime fecha = Fechas.ahora();

    protected MensajeSolicitud() { }

    MensajeSolicitud(Solicitud solicitud, Usuario autor, TipoMensaje tipo, String contenido, MensajeSolicitud respondeA) {
        this.solicitud = solicitud;
        this.autor = autor;
        this.tipo = tipo;
        this.contenido = contenido;
        this.respondeA = respondeA;
    }

    public UUID getId() { return id; }
    public Solicitud getSolicitud() { return solicitud; }
    public Usuario getAutor() { return autor; }
    public TipoMensaje getTipo() { return tipo; }
    public String getContenido() { return contenido; }
    public MensajeSolicitud getRespondeA() { return respondeA; }
    public OffsetDateTime getFecha() { return fecha; }

    @Override public boolean equals(Object o) { return o instanceof MensajeSolicitud && Objects.equals(id, ((MensajeSolicitud) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
