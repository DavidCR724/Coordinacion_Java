package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import modelo.entity.Carrera;
import modelo.entity.OrigenDatos;
import modelo.entity.Rol;
import modelo.entity.Usuario;

/**
 * Datos del usuario en sesión que leen las vistas (barra superior, perfil, formulario).
 * Es una copia: los permisos NO se deciden con ella, el EJB recarga al usuario en cada operación.
 */
public class UsuarioVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String rol;
    private final String etiquetaRol;
    private final String nombres;
    private final String paterno;
    private final String materno;
    private final String correo;
    private final String carrera;
    private final String control;
    private final Integer semestre;
    private final List<String> carreras;
    private final String idCarrera;
    private final OrigenDatos origen;

    public UsuarioVista(Usuario u) {
        id = u.getId();
        rol = codigoRol(u.getRol());
        etiquetaRol = u.getRol().getEtiqueta();
        nombres = u.getNombres();
        paterno = u.getApellidoPaterno();
        materno = u.getApellidoMaterno();
        correo = u.getCorreo();
        control = u.getNumControl() == null ? "" : u.getNumControl();
        semestre = u.getSemestre() == null ? null : u.getSemestre().intValue();
        idCarrera = u.getCarrera() == null ? null : u.getCarrera().getId().toString();
        origen = u.getOrigenDatos();
        List<String> claves = new ArrayList<>();
        List<String> nombresCarreras = new ArrayList<>();
        for (Carrera c : u.getCarrerasAsignadas()) {
            claves.add(c.getClave());
            nombresCarreras.add(c.getNombre());
        }
        Collections.sort(claves);
        Collections.sort(nombresCarreras);
        carreras = Collections.unmodifiableList(claves);
        if (u.getRol() == Rol.ESTUDIANTE) {
            carrera = u.getCarrera() == null ? "Carrera sin asignar" : u.getCarrera().getNombre();
        } else if (u.getRol() == Rol.JEFE) {
            carrera = "División de Estudios Profesionales";
        } else {
            carrera = nombresCarreras.isEmpty() ? "Sin carreras asignadas" : String.join(", ", nombresCarreras);
        }
    }

    /** Los códigos de rol que ya usan las vistas (vienen del front en React). */
    static String codigoRol(Rol r) {
        switch (r) {
            case COORDINADOR: return "coordinator";
            case JEFE: return "head";
            default: return "student";
        }
    }

    public UUID getId() { return id; }
    public String getRol() { return rol; }
    public String getEtiquetaRol() { return etiquetaRol; }
    public String getNombres() { return nombres; }
    public String getPaterno() { return paterno; }
    public String getMaterno() { return materno; }
    public String getNombreCompleto() {
        return (nombres + " " + paterno + (materno == null || materno.isEmpty() ? "" : " " + materno)).trim();
    }
    public String getIniciales() { return Formato.iniciales(nombres, paterno); }
    public String getCorreo() { return correo; }
    public String getCarrera() { return carrera; }
    public String getControl() { return control; }
    public Integer getSemestre() { return semestre; }
    public String getIdCarrera() { return idCarrera; }
    /** De dónde salen carrera y semestre (solo estudiantes): AUTODECLARADO, PADRON o VALIDADO. */
    public String getOrigen() { return origen == null ? "" : origen.name(); }
    public String getOrigenEtiqueta() { return origen == null ? "" : origen.getEtiqueta(); }
    public String getOrigenExplicacion() { return origen == null ? "" : origen.getExplicacion(); }
    /** Claves de las carreras que atiende (coordinación). */
    public List<String> getCarreras() { return carreras; }
}
