package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * «ORM persistable» Tabla USUARIO: toda persona que entra al sistema.
 *
 * El modelo de clases dibuja Alumno y PersonalCoordinacion como especializaciones de Usuario.
 * Aquí se implementan en una sola clase con el campo rol: el rol de una persona puede cambiar
 * (HU 6.3) y JPA no permite cambiar la subclase de un registro ya guardado.
 * Los datos de estudiante (carrera, número de control, semestre) solo aplican si rol = ESTUDIANTE;
 * las carreras que atiende coordinación están en carrerasAsignadas (tabla COORDINACION_CARRERA).
 */
@Entity
@Table(name = "USUARIO")
public class Usuario implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_USUARIO", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "NOMBRES", nullable = false)
    private String nombres;

    @Column(name = "APELLIDO_P", nullable = false)
    private String apellidoPaterno;

    @Column(name = "APELLIDO_M")
    private String apellidoMaterno;

    @Column(name = "CORREO", nullable = false, unique = true)
    private String correo;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROL", nullable = false)
    private Rol rol = Rol.ESTUDIANTE;

    /** Carrera del estudiante. NULL en los demás roles. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ID_CARRERA")
    private Carrera carrera;

    @Column(name = "NUM_CONTROL", unique = true)
    private String numControl;

    @Column(name = "SEMESTRE")
    private Short semestre;

    /** Solo estudiantes: de dónde salen carrera y semestre (HU 6.4, 6.5). */
    @Enumerated(EnumType.STRING)
    @Column(name = "ORIGEN_DATOS")
    private OrigenDatos origenDatos;

    @Column(name = "ACTIVO", nullable = false)
    private boolean activo = true;

    @Column(name = "FECHA_ALTA", nullable = false, updatable = false)
    private OffsetDateTime fechaAlta = Fechas.ahora();

    @Column(name = "ULTIMO_ACCESO")
    private OffsetDateTime ultimoAcceso;

    /** Carreras que atiende una persona de coordinación (N:M). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "COORDINACION_CARRERA",
               joinColumns = @JoinColumn(name = "ID_USUARIO"),
               inverseJoinColumns = @JoinColumn(name = "ID_CARRERA"))
    private Set<Carrera> carrerasAsignadas = new HashSet<>();

    public Usuario() { }

    /** Alumno nuevo. El número de control viene del correo o del padrón, nunca de lo que capture el alumno (HU 6.2). */
    public static Usuario estudiante(String correo, String nombres, String apellidoPaterno, String apellidoMaterno,
                                     String numControl, Carrera carrera, Short semestre, OrigenDatos origen) {
        Usuario u = new Usuario();
        u.setCorreo(correo);
        u.nombrar(nombres, apellidoPaterno, apellidoMaterno);
        u.numControl = numControl;
        u.actualizarDatosEscolares(carrera, semestre, origen);
        return u;
    }

    /** Cuenta de personal (coordinación o jefatura). El rol nunca se deduce del correo (HU 6.3). */
    public static Usuario personal(String correo, String nombres, String apellidoPaterno, String apellidoMaterno,
                                   Rol rol, Set<Carrera> carreras) {
        Usuario u = new Usuario();
        u.setCorreo(correo);
        u.nombrar(nombres, apellidoPaterno, apellidoMaterno);
        u.asignarRol(rol, carreras);
        return u;
    }

    // ---------- reglas de cambio ----------

    public final void nombrar(String nombres, String apellidoPaterno, String apellidoMaterno) {
        this.nombres = nombres;
        this.apellidoPaterno = apellidoPaterno;
        this.apellidoMaterno = apellidoMaterno;
    }

    /** Carrera y semestre del estudiante, con su origen (primer acceso, padrón o coordinación). */
    public final void actualizarDatosEscolares(Carrera carrera, Short semestre, OrigenDatos origen) {
        if (rol != Rol.ESTUDIANTE) {
            throw new IllegalStateException("Solo un estudiante tiene datos escolares.");
        }
        this.carrera = carrera;
        this.semestre = semestre;
        this.origenDatos = origen;
    }

    /**
     * HU 6.3: rol de personal y, si es coordinación, sus carreras. Un estudiante no se convierte
     * en personal (tiene número de control y solicitudes propias): se le crea otra cuenta.
     */
    public final void asignarRol(Rol nuevo, Set<Carrera> carreras) {
        if (nuevo == Rol.ESTUDIANTE || (rol == Rol.ESTUDIANTE && numControl != null)) {
            throw new IllegalStateException("Los roles de personal no aplican a cuentas de estudiante.");
        }
        rol = nuevo;
        carrerasAsignadas.clear();
        if (nuevo == Rol.COORDINADOR && carreras != null) {
            carrerasAsignadas.addAll(carreras);
        }
    }

    // ---------- reglas de lectura ----------

    public boolean esEstudiante() { return rol == Rol.ESTUDIANTE; }
    public boolean esCoordinador() { return rol == Rol.COORDINADOR; }
    public boolean esJefe() { return rol == Rol.JEFE; }

    /** La jefatura atiende todas las carreras; coordinación solo las asignadas. */
    public boolean atiendeCarrera(Carrera c) {
        if (esJefe()) {
            return true;
        }
        return esCoordinador() && c != null && carrerasAsignadas.contains(c);
    }

    public String getNombreCompleto() {
        StringBuilder sb = new StringBuilder(nombres).append(' ').append(apellidoPaterno);
        if (apellidoMaterno != null && !apellidoMaterno.trim().isEmpty()) {
            sb.append(' ').append(apellidoMaterno);
        }
        return sb.toString();
    }

    // ---------- getters y setters ----------

    public UUID getId() { return id; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidoPaterno() { return apellidoPaterno; }
    public void setApellidoPaterno(String apellidoPaterno) { this.apellidoPaterno = apellidoPaterno; }
    public String getApellidoMaterno() { return apellidoMaterno; }
    public void setApellidoMaterno(String apellidoMaterno) { this.apellidoMaterno = apellidoMaterno; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo == null ? null : correo.trim().toLowerCase(); }
    public Rol getRol() { return rol; }
    public Carrera getCarrera() { return carrera; }
    public String getNumControl() { return numControl; }
    public Short getSemestre() { return semestre; }
    public OrigenDatos getOrigenDatos() { return origenDatos; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public OffsetDateTime getFechaAlta() { return fechaAlta; }
    public OffsetDateTime getUltimoAcceso() { return ultimoAcceso; }
    public void setUltimoAcceso(OffsetDateTime ultimoAcceso) { this.ultimoAcceso = ultimoAcceso; }
    public Set<Carrera> getCarrerasAsignadas() { return carrerasAsignadas; }

    @Override public boolean equals(Object o) { return o instanceof Usuario && Objects.equals(id, ((Usuario) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
    @Override public String toString() { return correo; }
}
