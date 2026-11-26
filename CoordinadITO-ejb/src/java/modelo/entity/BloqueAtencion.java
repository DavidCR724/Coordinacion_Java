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

/** «ORM persistable» Tabla BLOQUE_ATENCION: un espacio de atención presencial (HU 9.1). */
@Entity
@Table(name = "BLOQUE_ATENCION")
public class BloqueAtencion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_BLOQUE", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_COORDINADOR", nullable = false, updatable = false)
    private Usuario coordinador;

    @Column(name = "INICIO", nullable = false, updatable = false)
    private OffsetDateTime inicio;

    @Column(name = "FIN", nullable = false, updatable = false)
    private OffsetDateTime fin;

    @Column(name = "LUGAR", nullable = false)
    private String lugar;

    @Column(name = "CANCELADO", nullable = false)
    private boolean cancelado;

    @Column(name = "FECHA_ALTA", nullable = false, updatable = false)
    private OffsetDateTime fechaAlta = Fechas.ahora();

    protected BloqueAtencion() { }

    public BloqueAtencion(Usuario coordinador, OffsetDateTime inicio, OffsetDateTime fin, String lugar) {
        this.coordinador = coordinador;
        this.inicio = inicio;
        this.fin = fin;
        this.lugar = lugar;
    }

    public boolean isFuturo() {
        return inicio.isAfter(Fechas.ahora());
    }

    public void cancelar() {
        cancelado = true;
    }

    public UUID getId() { return id; }
    public Usuario getCoordinador() { return coordinador; }
    public OffsetDateTime getInicio() { return inicio; }
    public OffsetDateTime getFin() { return fin; }
    public String getLugar() { return lugar; }
    public boolean isCancelado() { return cancelado; }

    @Override public boolean equals(Object o) { return o instanceof BloqueAtencion && Objects.equals(id, ((BloqueAtencion) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
