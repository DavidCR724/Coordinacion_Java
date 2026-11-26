package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * «ORM persistable» Tabla NOTIFICACION (HU 5.1, 5.2).
 * Se crea en la misma transacción que el cambio que la provoca; el correo sale después
 * (CorreoService) y marca correoEnviadoEn.
 */
@Entity
@Table(name = "NOTIFICACION")
public class Notificacion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_NOTIFICACION", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_DESTINATARIO", nullable = false, updatable = false)
    private Usuario destinatario;

    @ManyToOne
    @JoinColumn(name = "ID_SOLICITUD", updatable = false)
    private Solicitud solicitud;

    @Column(name = "TITULO", nullable = false, updatable = false)
    private String titulo;

    @Column(name = "MENSAJE", nullable = false, updatable = false)
    private String mensaje;

    @Column(name = "FECHA", nullable = false, updatable = false)
    private OffsetDateTime fecha = Fechas.ahora();

    @Column(name = "CORREO_ENVIADO_EN")
    private OffsetDateTime correoEnviadoEn;

    @Column(name = "LEIDA_EN")
    private OffsetDateTime leidaEn;

    protected Notificacion() { }

    public Notificacion(Usuario destinatario, Solicitud solicitud, String titulo, String mensaje) {
        this.destinatario = destinatario;
        this.solicitud = solicitud;
        this.titulo = titulo;
        this.mensaje = mensaje;
    }

    public boolean isLeida() { return leidaEn != null; }

    public UUID getId() { return id; }
    public Usuario getDestinatario() { return destinatario; }
    public Solicitud getSolicitud() { return solicitud; }
    public String getTitulo() { return titulo; }
    public String getMensaje() { return mensaje; }
    public OffsetDateTime getFecha() { return fecha; }
    public OffsetDateTime getCorreoEnviadoEn() { return correoEnviadoEn; }
    public void setCorreoEnviadoEn(OffsetDateTime correoEnviadoEn) { this.correoEnviadoEn = correoEnviadoEn; }
    public OffsetDateTime getLeidaEn() { return leidaEn; }
    public void setLeidaEn(OffsetDateTime leidaEn) { this.leidaEn = leidaEn; }

    @Override public boolean equals(Object o) { return o instanceof Notificacion && Objects.equals(id, ((Notificacion) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
