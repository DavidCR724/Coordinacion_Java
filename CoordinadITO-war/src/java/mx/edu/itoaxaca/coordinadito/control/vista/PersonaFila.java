package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import modelo.entity.Carrera;
import modelo.entity.Usuario;

/**
 * Una cuenta en una lista: alumnos (adheridos a un grupo, búsqueda de alumnos) y personal (roles).
 * «fecha» es la que importa en cada lista (cuándo se sumó, cuándo se dio de alta).
 */
public class PersonaFila implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String nombre;
    private final String iniciales;
    private final String correo;
    private final String control;
    private final String carrera;
    private final String claveCarrera;
    private final Integer semestre;
    private final String origen;
    private final String origenEtiqueta;
    private final boolean activo;
    private final String fecha;
    private final String rol;
    private final String etiquetaRol;
    private final List<String> carrerasAsignadas = new ArrayList<>();
    private final List<String> idsCarreras = new ArrayList<>();
    private final String ultimoAcceso;

    public PersonaFila(Usuario u, OffsetDateTime fecha) {
        id = u.getId().toString();
        nombre = u.getNombreCompleto();
        iniciales = Formato.iniciales(u.getNombres(), u.getApellidoPaterno());
        correo = u.getCorreo();
        control = u.getNumControl() == null ? "" : u.getNumControl();
        carrera = u.getCarrera() == null ? "" : u.getCarrera().getNombre();
        claveCarrera = u.getCarrera() == null ? "" : u.getCarrera().getClave();
        semestre = u.getSemestre() == null ? null : u.getSemestre().intValue();
        origen = u.getOrigenDatos() == null ? "" : u.getOrigenDatos().name();
        origenEtiqueta = u.getOrigenDatos() == null ? "" : u.getOrigenDatos().getEtiqueta();
        activo = u.isActivo();
        this.fecha = Formato.fecha(fecha);
        rol = u.getRol().name();
        etiquetaRol = u.getRol().getEtiqueta();
        for (Carrera c : u.getCarrerasAsignadas()) {
            carrerasAsignadas.add(c.getClave());
            idsCarreras.add(c.getId().toString());
        }
        Collections.sort(carrerasAsignadas);
        ultimoAcceso = u.getUltimoAcceso() == null ? "Nunca" : Formato.fechaHora(u.getUltimoAcceso());
    }

    /** Cuenta con su fecha de alta como fecha de la fila. */
    public static PersonaFila cuenta(Usuario u) {
        return new PersonaFila(u, u.getFechaAlta());
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getIniciales() { return iniciales; }
    public String getCorreo() { return correo; }
    public String getControl() { return control; }
    public String getCarrera() { return carrera; }
    public String getClaveCarrera() { return claveCarrera; }
    public Integer getSemestre() { return semestre; }
    public String getOrigen() { return origen; }
    public String getOrigenEtiqueta() { return origenEtiqueta; }
    public boolean isActivo() { return activo; }
    public String getFecha() { return fecha; }
    /** ESTUDIANTE | COORDINADOR | JEFE */
    public String getRol() { return rol; }
    public String getEtiquetaRol() { return etiquetaRol; }
    /** Claves de las carreras que atiende (coordinación). */
    public List<String> getCarrerasAsignadas() { return carrerasAsignadas; }
    public List<String> getIdsCarreras() { return idsCarreras; }
    public String getUltimoAcceso() { return ultimoAcceso; }
}
