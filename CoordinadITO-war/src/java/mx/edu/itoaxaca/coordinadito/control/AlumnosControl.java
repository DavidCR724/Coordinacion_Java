package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.UUID;
import mx.edu.itoaxaca.coordinadito.control.vista.ListaPaginada;
import mx.edu.itoaxaca.coordinadito.control.vista.PersonaFila;
import modelo.bean.AlumnoService;
import modelo.entity.Usuario;

/**
 * «control» Alumnos de las carreras que atiende quien consulta (vista/alumnos.xhtml, HU 6.5).
 * Búsqueda por nombre, número de control o correo, filtrada por carrera, paginada en la base.
 */
@Named
@ViewScoped
public class AlumnosControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private AlumnoService alumnoService;

    private String carrera;
    private String texto;

    private final ListaPaginada<Usuario, PersonaFila> alumnos = new ListaPaginada<>(
            (numero, tamano) -> alumnoService.buscar(yo(), carrera == null || carrera.isEmpty() ? null : UUID.fromString(carrera),
                    texto, numero, tamano),
            PersonaFila::cuenta, PersonaFila::getId);

    public ListaPaginada<Usuario, PersonaFila> getAlumnos() { return alumnos; }
    public String getCarrera() { return carrera; }
    public void setCarrera(String carrera) { this.carrera = carrera; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
}
