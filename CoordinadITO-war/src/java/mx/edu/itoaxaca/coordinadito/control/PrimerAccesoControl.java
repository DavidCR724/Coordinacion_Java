package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.UUID;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import modelo.bean.AccesoService;
import modelo.bean.Identidad;
import modelo.entity.Usuario;

/**
 * «control» Primer acceso del estudiante que no está en el padrón (vista/primer-acceso.xhtml, HU 6.4).
 * Declara carrera y semestre; la cuenta queda con datos AUTODECLARADO hasta que llegue el padrón.
 */
@Named
@ViewScoped
public class PrimerAccesoControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private AccesoService accesoService;

    private String carrera;
    private Integer semestre;
    /** Solo si el proveedor no entregó el nombre (acceso de desarrollo). */
    private String nombres;
    private String apellidos;

    public String guardar() {
        Identidad p = sesion.getPendiente();
        Identidad id = isPideNombre() ? new Identidad(p.getCorreo(), nombres, apellidos) : p;
        Usuario nuevo = Mensajes.intentar(() -> accesoService.completarPrimerAcceso(id, UUID.fromString(carrera), semestre));
        if (nuevo == null) {
            return null;
        }
        sesion.completarPrimerAcceso(nuevo);
        Mensajes.info("¡Listo!", "Tu expediente quedó completo. Ya puedes registrar solicitudes.");
        return Mensajes.redirigir("/vista/inicio");
    }

    public Identidad getIdentidad() { return sesion.getPendiente(); }
    public boolean isPideNombre() {
        Identidad p = sesion.getPendiente();
        return p != null && (p.getNombres() == null || p.getApellidos() == null);
    }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getCarrera() { return carrera; }
    public void setCarrera(String carrera) { this.carrera = carrera; }
    public Integer getSemestre() { return semestre; }
    public void setSemestre(Integer semestre) { this.semestre = semestre; }
}
