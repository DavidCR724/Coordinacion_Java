package modelo.bean;

import acceso.datos.SolicitudRepository;
import acceso.datos.UsuarioRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import modelo.entity.Accion;
import modelo.entity.Carrera;
import modelo.entity.OrigenDatos;
import modelo.entity.Solicitud;
import modelo.entity.Usuario;

/**
 * «servicio» Expediente de alumnos para coordinación (HU 6.5): búsqueda, detalle y corrección
 * de carrera y semestre. La corrección queda marcada como VALIDADO y en la auditoría.
 */
@Stateless
public class AlumnoService {

    @EJB private UsuarioRepository usuarios;
    @EJB private SolicitudRepository solicitudes;
    @EJB private CatalogoService catalogo;
    @EJB private Guardia guardia;
    @EJB private AuditoriaService auditoria;

    /** Alumnos de las carreras que atiende quien consulta (jefatura: todas). */
    public Pagina<Usuario> buscar(UUID idActor, UUID idCarrera, String texto, int pagina, int tamano) {
        Set<Carrera> carreras = SolicitudService.carrerasDe(guardia.exigir(idActor, Permiso.ATENDER));
        int p = Math.max(0, pagina);
        return new Pagina<>(usuarios.alumnos(carreras, idCarrera, texto, p * tamano, tamano),
                usuarios.contarAlumnos(carreras, idCarrera, texto), p, tamano);
    }

    public Usuario detalle(UUID idActor, UUID idAlumno) {
        Usuario actor = guardia.exigir(idActor, Permiso.ATENDER);
        Usuario alumno = alumnoExistente(idAlumno);
        guardia.exigirCarrera(actor, alumno.getCarrera());
        return alumno;
    }

    public List<Solicitud> solicitudesDe(UUID idActor, UUID idAlumno) {
        return solicitudes.deAlumno(detalle(idActor, idAlumno));
    }

    /** HU 6.5: corrige carrera y semestre; el dato queda «validado por coordinación». */
    public Usuario corregir(UUID idActor, UUID idAlumno, UUID idCarrera, Integer semestre) {
        Usuario actor = guardia.exigir(idActor, Permiso.ATENDER);
        Usuario alumno = alumnoExistente(idAlumno);
        guardia.exigirCarrera(actor, alumno.getCarrera());
        Carrera nueva = catalogo.carrerasPorId(java.util.Collections.singleton(Reglas.requerido(idCarrera, "Elige la carrera.")))
                .iterator().next();
        short sem = Reglas.semestre(semestre);
        if (nueva.equals(alumno.getCarrera()) && Objects.equals(alumno.getSemestre(), sem)
                && alumno.getOrigenDatos() == OrigenDatos.VALIDADO) {
            throw new ReglaNegocioException("No hay cambios que guardar.");
        }
        String antes = alumno.getCarrera().getClave() + " / " + alumno.getSemestre() + " (" + alumno.getOrigenDatos() + ")";
        alumno.actualizarDatosEscolares(nueva, sem, OrigenDatos.VALIDADO);
        auditoria.registrar(actor, Accion.CORREGIR_ALUMNO, "USUARIO", alumno.getId(), "numControl", alumno.getNumControl(),
                "antes", antes, "despues", nueva.getClave() + " / " + sem);
        return alumno;
    }

    private Usuario alumnoExistente(UUID id) {
        Usuario u = usuarios.buscar(id).orElseThrow(() -> new NoEncontradoException("El alumno no existe."));
        if (!u.esEstudiante()) {
            throw new NoEncontradoException("El alumno no existe.");
        }
        return u;
    }
}
