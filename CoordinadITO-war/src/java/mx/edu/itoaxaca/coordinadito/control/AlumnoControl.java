package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.PersonaFila;
import mx.edu.itoaxaca.coordinadito.control.vista.SolicitudFila;
import modelo.bean.AlumnoService;
import modelo.entity.Solicitud;
import modelo.entity.Usuario;

/**
 * «control» Expediente de un alumno (vista/alumno-detalle.xhtml, HU 6.5): sus datos, de dónde
 * salen (padrón, autodeclarados o validados) y sus solicitudes. Coordinación y jefatura corrigen
 * carrera y semestre; el dato queda «validado por coordinación» y la corrección, en la auditoría.
 */
@Named
@ViewScoped
public class AlumnoControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private AlumnoService alumnoService;

    /** ?id= */
    private String id;
    private PersonaFila alumno;
    private final List<SolicitudFila> solicitudes = new ArrayList<>();
    private String carrera;
    private Integer semestre;

    /** f:viewAction */
    public void cargar() {
        if (alumno == null) {
            recargar();
        }
    }

    private void recargar() {
        UUID uuid = uuid();
        Usuario u = uuid == null ? null : Mensajes.intentar(() -> alumnoService.detalle(yo(), uuid));
        alumno = u == null ? null : PersonaFila.cuenta(u);
        solicitudes.clear();
        if (u != null) {
            carrera = u.getCarrera() == null ? null : u.getCarrera().getId().toString();
            semestre = u.getSemestre() == null ? null : u.getSemestre().intValue();
            List<Solicitud> r = Mensajes.intentar(() -> alumnoService.solicitudesDe(yo(), uuid));
            if (r != null) {
                r.forEach(s -> solicitudes.add(new SolicitudFila(s)));
            }
        }
    }

    public void corregir() {
        if (hecho(() -> alumnoService.corregir(yo(), uuid(), carrera == null ? null : UUID.fromString(carrera), semestre),
                "Datos validados", "La carrera y el semestre quedaron como «validados por coordinación».")) {
            recargar();
        }
    }

    private UUID uuid() {
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isVisible() { return alumno != null; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public PersonaFila getAlumno() { return alumno; }
    public List<SolicitudFila> getSolicitudes() { return solicitudes; }
    public String getCarrera() { return carrera; }
    public void setCarrera(String carrera) { this.carrera = carrera; }
    public Integer getSemestre() { return semestre; }
    public void setSemestre(Integer semestre) { this.semestre = semestre; }
}
