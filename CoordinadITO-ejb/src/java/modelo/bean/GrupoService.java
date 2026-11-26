package modelo.bean;

import acceso.datos.MateriaRepository;
import acceso.datos.SolicitudRepository;
import acceso.datos.TramiteRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import modelo.entity.Carrera;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Materia;
import modelo.entity.Solicitud;
import modelo.entity.Tramite;
import modelo.entity.Usuario;

/**
 * «servicio» Apertura de grupos (HU 4.1 a 4.4): una sola lista por materia.
 *
 * Una apertura es una Solicitud del trámite colectivo con materia. Se atiende igual que cualquier
 * solicitud (bandeja, estados, bitácora): aquí solo vive lo propio de los grupos, es decir, pedirla,
 * adherirse, retirarse y consultar la demanda.
 */
@Stateless
public class GrupoService {

    @EJB private TramiteRepository tramites;
    @EJB private MateriaRepository materias;
    @EJB private SolicitudRepository solicitudes;
    @EJB private SolicitudService solicitudService;
    @EJB private NotificacionService notificaciones;
    @EJB private Guardia guardia;

    /** Materias de la carrera del alumno, para pedir una apertura (HU 4.1). */
    public List<Materia> materiasParaAlumno(UUID idAlumno) {
        return materias.deCarrera(solicitudService.exigirAlumnoConCarrera(idAlumno).getCarrera());
    }

    /**
     * HU 4.2 y 4.4: aperturas visibles para quien consulta, de la más demandada a la menos.
     * Estudiante: las de su carrera. Coordinación: las de sus carreras. Jefatura: todas.
     */
    public List<GrupoResumen> grupos(UUID idUsuario, boolean soloAbiertas, String texto) {
        Usuario u = guardia.usuario(idUsuario);
        Set<Carrera> carreras;
        if (u.esEstudiante()) {
            carreras = u.getCarrera() == null ? Collections.<Carrera>emptySet() : Collections.singleton(u.getCarrera());
        } else if (Permiso.ATENDER.permite(u.getRol())) {
            carreras = SolicitudService.carrerasDe(u);
        } else {
            throw new AccesoDenegadoException(Permiso.ATENDER.getMensajeNegado());
        }
        List<Solicitud> lista = solicitudes.grupos(carreras, soloAbiertas ? EstadoSolicitud.abiertos() : null, texto);
        Map<UUID, Long> adhesiones = solicitudes.adhesionesPor(lista);
        Set<UUID> mias = u.esEstudiante() ? solicitudes.adheridasPor(u) : Collections.<UUID>emptySet();
        List<GrupoResumen> r = new ArrayList<>();
        for (Solicitud s : lista) {
            r.add(new GrupoResumen(s, 1 + adhesiones.getOrDefault(s.getId(), 0L),
                    s.getSolicitante().equals(u), mias.contains(s.getId())));
        }
        r.sort(Comparator.comparingLong(GrupoResumen::getInteresados).reversed()
                .thenComparing(g -> g.getSolicitud().getFechaRegistro()));
        return r;
    }

    /**
     * HU 4.1: pide abrir un grupo. Si la materia ya tiene una apertura activa, lanza
     * {@link GrupoExistenteException} con su folio para que el alumno se adhiera (HU 4.2).
     */
    public Solicitud solicitarApertura(UUID idAlumno, UUID idMateria, String motivo) {
        Usuario alumno = solicitudService.exigirAlumnoConCarrera(idAlumno);
        Tramite tramite = tramites.colectivo()
                .orElseThrow(() -> new ReglaNegocioException("La apertura de grupos no está habilitada en el catálogo."));
        Reglas.disponible(tramite);
        Materia materia = materias.buscar(Reglas.requerido(idMateria, "Elige la materia."))
                .orElseThrow(() -> new NoEncontradoException("La materia no existe."));
        if (!materia.getCarrera().equals(alumno.getCarrera())) {
            throw new ReglaNegocioException("Solo puedes pedir grupos de materias de tu carrera.");
        }
        solicitudes.grupoAbiertoDe(materia).ifPresent(s -> {
            throw new GrupoExistenteException(s.getFolio());
        });
        Solicitud s = Solicitud.aperturaDeGrupo(tramite, alumno, materia, Reglas.opcional(motivo, "El motivo"));
        solicitudService.guardarNueva(s, "uq_grupo_abierto_materia", () -> new GrupoExistenteException(null));
        notificaciones.avisar(alumno, s, "Pediste abrir " + materia.getNombre(),
                "Tu solicitud " + s.getFolioTexto() + " quedó registrada. Comparte el folio: tus compañeros pueden sumarse.");
        solicitudService.avisarCoordinacion(s, "Apertura de grupo de " + materia.getNombre() + ".");
        return s;
    }

    /** HU 4.2: el alumno se suma a la lista de una apertura activa de su carrera. */
    public Solicitud adherirse(UUID idAlumno, int folio) {
        Usuario alumno = solicitudService.exigirAlumnoConCarrera(idAlumno);
        Solicitud s = aperturaAbierta(folio);
        if (!s.getMateria().getCarrera().equals(alumno.getCarrera())) {
            throw new ReglaNegocioException("Solo puedes sumarte a grupos de tu carrera.");
        }
        if (s.esInteresado(alumno)) {
            throw new ReglaNegocioException("Ya estás en la lista de esta solicitud.");
        }
        s.adherir(alumno);
        notificaciones.avisar(alumno, s, "Te sumaste a " + s.getFolioTexto(),
                "Estás en la lista para abrir " + s.getMateria().getNombre() + ". Te avisaremos de cada cambio.");
        return s;
    }

    /** HU 4.2: el alumno retira su adhesión y el contador baja. */
    public Solicitud retirarse(UUID idAlumno, int folio) {
        Usuario alumno = guardia.exigir(idAlumno, Permiso.SOLICITAR);
        Solicitud s = aperturaAbierta(folio);
        if (s.getSolicitante().equals(alumno)) {
            throw new ReglaNegocioException("Tú registraste esta solicitud; no puedes retirarte de ella.");
        }
        if (!s.tieneAdherido(alumno)) {
            throw new ReglaNegocioException("No estás en la lista de esta solicitud.");
        }
        s.retirarAdhesion(alumno);
        return s;
    }

    private Solicitud aperturaAbierta(int folio) {
        Solicitud s = solicitudService.existente(folio);
        if (!s.isColectiva()) {
            throw new ReglaNegocioException("Esa solicitud no es una apertura de grupo.");
        }
        if (!s.getEstado().isAbierto()) {
            throw new ReglaNegocioException("Esa solicitud ya está cerrada.");
        }
        return s;
    }
}
