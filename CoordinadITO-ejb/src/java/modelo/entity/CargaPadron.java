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

/** «ORM persistable» Tabla CARGA_PADRON: cada archivo de padrón procesado (HU 8.1). */
@Entity
@Table(name = "CARGA_PADRON")
public class CargaPadron implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_CARGA", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    /** Quien subió el archivo: una coordinación (sus carreras) o jefatura (bd/06 renombró la columna). */
    @JoinColumn(name = "ID_RESPONSABLE", nullable = false, updatable = false)
    private Usuario responsable;

    @Column(name = "NOMBRE_ARCHIVO", nullable = false)
    private String nombreArchivo;

    @Column(name = "FECHA", nullable = false, updatable = false)
    private OffsetDateTime fecha = Fechas.ahora();

    @Column(name = "TOTAL_REGISTROS", nullable = false)
    private int total;

    @Column(name = "NUEVOS", nullable = false)
    private int nuevos;

    @Column(name = "ACTUALIZADOS", nullable = false)
    private int actualizados;

    @Column(name = "RECHAZADOS", nullable = false)
    private int rechazados;

    protected CargaPadron() { }

    public CargaPadron(Usuario responsable, String nombreArchivo, int total) {
        this.responsable = responsable;
        this.nombreArchivo = nombreArchivo;
        this.total = total;
    }

    public void contarNuevo() { nuevos++; }
    public void contarActualizado() { actualizados++; }
    public void contarRechazado() { rechazados++; }

    public UUID getId() { return id; }
    public Usuario getResponsable() { return responsable; }
    public String getNombreArchivo() { return nombreArchivo; }
    public OffsetDateTime getFecha() { return fecha; }
    public int getTotal() { return total; }
    public int getNuevos() { return nuevos; }
    public int getActualizados() { return actualizados; }
    public int getRechazados() { return rechazados; }
    /** Sin cambios: filas idénticas a las que ya estaban. */
    public int getSinCambios() { return total - nuevos - actualizados - rechazados; }

    @Override public boolean equals(Object o) { return o instanceof CargaPadron && Objects.equals(id, ((CargaPadron) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
