package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/**
 * «ORM persistable» Tabla PADRON_ALUMNO: el padrón oficial de inscritos (HU 6.2, 6.4, 8.1).
 * Es la fuente institucional de la identidad del estudiante.
 */
@Entity
@Table(name = "PADRON_ALUMNO")
public class PadronAlumno implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "NUM_CONTROL", nullable = false, updatable = false)
    private String numControl;

    @Column(name = "CORREO", nullable = false, unique = true)
    private String correo;

    @Column(name = "NOMBRES", nullable = false)
    private String nombres;

    @Column(name = "APELLIDO_P", nullable = false)
    private String apellidoPaterno;

    @Column(name = "APELLIDO_M")
    private String apellidoMaterno;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_CARRERA", nullable = false)
    private Carrera carrera;

    @Column(name = "SEMESTRE")
    private Short semestre;

    @Column(name = "VIGENTE", nullable = false)
    private boolean vigente = true;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_CARGA", nullable = false)
    private CargaPadron carga;

    protected PadronAlumno() { }

    public PadronAlumno(String numControl) {
        this.numControl = numControl;
    }

    /**
     * Copia los datos de una fila del archivo. Devuelve true si algo cambió
     * (para contar «actualizados» contra «sin cambios»).
     */
    public boolean actualizar(String correo, String nombres, String apellidoPaterno, String apellidoMaterno,
                              Carrera carrera, Short semestre, CargaPadron carga) {
        boolean cambio = !vigente || !Objects.equals(this.correo, correo) || !Objects.equals(this.nombres, nombres)
                || !Objects.equals(this.apellidoPaterno, apellidoPaterno)
                || !Objects.equals(this.apellidoMaterno, apellidoMaterno)
                || !Objects.equals(this.carrera, carrera) || !Objects.equals(this.semestre, semestre);
        this.correo = correo;
        this.nombres = nombres;
        this.apellidoPaterno = apellidoPaterno;
        this.apellidoMaterno = apellidoMaterno;
        this.carrera = carrera;
        this.semestre = semestre;
        this.vigente = true;
        this.carga = carga;
        return cambio;
    }

    public String getNumControl() { return numControl; }
    public String getCorreo() { return correo; }
    public String getNombres() { return nombres; }
    public String getApellidoPaterno() { return apellidoPaterno; }
    public String getApellidoMaterno() { return apellidoMaterno; }
    public Carrera getCarrera() { return carrera; }
    public Short getSemestre() { return semestre; }
    public boolean isVigente() { return vigente; }
    public void setVigente(boolean vigente) { this.vigente = vigente; }
    public CargaPadron getCarga() { return carga; }

    @Override public boolean equals(Object o) { return o instanceof PadronAlumno && Objects.equals(numControl, ((PadronAlumno) o).numControl); }
    @Override public int hashCode() { return Objects.hashCode(numControl); }
}
