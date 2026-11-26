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
 * «ORM persistable» Tabla DOCUMENTO_ADJUNTO (HU 1.5, 2.6).
 * La tabla solo guarda los datos del archivo; el contenido vive donde indique referencia
 * (lo decide el almacén de archivos, ver modelo.bean.AlmacenArchivos).
 */
@Entity
@Table(name = "DOCUMENTO_ADJUNTO")
public class DocumentoAdjunto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_DOCUMENTO", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_SOLICITUD", nullable = false, updatable = false)
    private Solicitud solicitud;

    @Column(name = "REFERENCIA", nullable = false)
    private String referencia;

    @Column(name = "NOMBRE_ORIGINAL", nullable = false)
    private String nombreOriginal;

    @Column(name = "MIME")
    private String mime;

    @Column(name = "TAMANO_BYTES")
    private Long tamanoBytes;

    @Column(name = "FECHA_SUBIDA", nullable = false, updatable = false)
    private OffsetDateTime fechaSubida = Fechas.ahora();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_SUBIDO_POR", nullable = false, updatable = false)
    private Usuario subidoPor;

    /** Mensaje al que acompaña (respuesta del alumno, HU 2.6), si aplica. */
    @ManyToOne
    @JoinColumn(name = "ID_MENSAJE", updatable = false)
    private MensajeSolicitud mensaje;

    protected DocumentoAdjunto() { }

    public DocumentoAdjunto(Solicitud solicitud, Usuario subidoPor, String nombreOriginal, String mime,
                            long tamanoBytes, String referencia, MensajeSolicitud mensaje) {
        this.solicitud = solicitud;
        this.subidoPor = subidoPor;
        this.nombreOriginal = nombreOriginal;
        this.mime = mime;
        this.tamanoBytes = tamanoBytes;
        this.referencia = referencia;
        this.mensaje = mensaje;
    }

    public UUID getId() { return id; }
    public Solicitud getSolicitud() { return solicitud; }
    public String getReferencia() { return referencia; }
    public String getNombreOriginal() { return nombreOriginal; }
    public String getMime() { return mime; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public OffsetDateTime getFechaSubida() { return fechaSubida; }
    public Usuario getSubidoPor() { return subidoPor; }
    public MensajeSolicitud getMensaje() { return mensaje; }

    @Override public boolean equals(Object o) { return o instanceof DocumentoAdjunto && Objects.equals(id, ((DocumentoAdjunto) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
