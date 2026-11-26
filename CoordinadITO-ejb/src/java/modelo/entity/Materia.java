package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * «ORM persistable» Tabla MATERIA. Se usa en la apertura de grupos (Iteración 3);
 * se mapea desde ahora porque SOLICITUD la referencia.
 */
@Entity
@Table(name = "MATERIA")
public class Materia implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_MATERIA", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "CLAVE", nullable = false, unique = true)
    private String clave;

    @Column(name = "NOMBRE", nullable = false)
    private String nombre;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_CARRERA", nullable = false)
    private Carrera carrera;

    @Column(name = "SEMESTRE")
    private Short semestre;

    @Column(name = "CREDITOS")
    private Short creditos;

    @Column(name = "ACTIVA", nullable = false)
    private boolean activa = true;

    public UUID getId() { return id; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Carrera getCarrera() { return carrera; }
    public void setCarrera(Carrera carrera) { this.carrera = carrera; }
    public Short getSemestre() { return semestre; }
    public void setSemestre(Short semestre) { this.semestre = semestre; }
    public Short getCreditos() { return creditos; }
    public void setCreditos(Short creditos) { this.creditos = creditos; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    @Override public boolean equals(Object o) { return o instanceof Materia && Objects.equals(id, ((Materia) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
