package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** «ORM persistable» Tabla CARRERA. */
@Entity
@Table(name = "CARRERA")
public class Carrera implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_CARRERA", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "CLAVE", nullable = false, unique = true)
    private String clave;

    @Column(name = "NOMBRE", nullable = false, unique = true)
    private String nombre;

    public Carrera() { }

    public Carrera(String clave, String nombre) {
        this.clave = clave;
        this.nombre = nombre;
    }

    public UUID getId() { return id; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    @Override public boolean equals(Object o) { return o instanceof Carrera && Objects.equals(id, ((Carrera) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
    @Override public String toString() { return clave; }
}
