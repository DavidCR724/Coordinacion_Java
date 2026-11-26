package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** «ORM persistable» Tabla AVISO: comunicado oficial de coordinación a una o varias carreras (HU 5.3). */
@Entity
@Table(name = "AVISO")
public class Aviso implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_AVISO", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_AUTOR", nullable = false, updatable = false)
    private Usuario autor;

    @Column(name = "TITULO", nullable = false)
    private String titulo;

    @Column(name = "CONTENIDO", nullable = false)
    private String contenido;

    @Column(name = "INICIO_VIGENCIA", nullable = false)
    private LocalDate inicioVigencia;

    @Column(name = "FIN_VIGENCIA", nullable = false)
    private LocalDate finVigencia;

    @Column(name = "FECHA_PUBLICACION", nullable = false, updatable = false)
    private OffsetDateTime fechaPublicacion = Fechas.ahora();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "AVISO_CARRERA",
               joinColumns = @JoinColumn(name = "ID_AVISO"),
               inverseJoinColumns = @JoinColumn(name = "ID_CARRERA"))
    private Set<Carrera> carreras = new HashSet<>();

    protected Aviso() { }

    public Aviso(Usuario autor, String titulo, String contenido, LocalDate inicio, LocalDate fin, Set<Carrera> carreras) {
        this.autor = autor;
        this.titulo = titulo;
        this.contenido = contenido;
        this.inicioVigencia = inicio;
        this.finVigencia = fin;
        this.carreras.addAll(carreras);
    }

    /** Vigente = hoy está entre el inicio y el fin (inclusive). */
    public boolean isVigente() {
        LocalDate hoy = Fechas.hoy();
        return !hoy.isBefore(inicioVigencia) && !hoy.isAfter(finVigencia);
    }

    /** Aún no empieza a mostrarse. */
    public boolean isProgramado() {
        return Fechas.hoy().isBefore(inicioVigencia);
    }

    /** HU 5.3: deja de mostrarse desde hoy; sigue consultable en el historial. */
    public void retirar() {
        LocalDate ayer = Fechas.hoy().minusDays(1);
        finVigencia = ayer;
        if (inicioVigencia.isAfter(ayer)) {
            inicioVigencia = ayer;
        }
    }

    public UUID getId() { return id; }
    public Usuario getAutor() { return autor; }
    public String getTitulo() { return titulo; }
    public String getContenido() { return contenido; }
    public LocalDate getInicioVigencia() { return inicioVigencia; }
    public LocalDate getFinVigencia() { return finVigencia; }
    public OffsetDateTime getFechaPublicacion() { return fechaPublicacion; }
    public Set<Carrera> getCarreras() { return carreras; }

    @Override public boolean equals(Object o) { return o instanceof Aviso && Objects.equals(id, ((Aviso) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
