package modelo.bean;

import acceso.datos.AgendaRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.PersistenceException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import modelo.entity.BloqueAtencion;
import modelo.entity.Cita;
import modelo.entity.EstadoCita;
import modelo.entity.Fechas;
import modelo.entity.Solicitud;
import modelo.entity.Usuario;

/**
 * «servicio» Agenda de citas presenciales (HU 9.1 a 9.3).
 *
 *   Coordinación publica bloques (9.1) → pide la cita en una solicitud (SolicitudService.requerirCita)
 *   → el alumno agenda en un bloque libre (9.2) → coordinación ve su agenda del día y registra
 *   la asistencia (9.3).
 * Un bloque ocupado deja de ofrecerse (lo garantiza también el índice UQ_CITA_BLOQUE de la base).
 */
@Stateless
public class AgendaService {

    /** Hasta cuántas horas antes de la cita el alumno puede cancelarla o cambiarla. */
    static final int PLAZO_HORAS = 12;
    private static final int MAX_BLOQUES_POR_DIA = 40;

    @EJB private AgendaRepository agenda;
    @EJB private SolicitudService solicitudService;
    @EJB private NotificacionService notificaciones;
    @EJB private Guardia guardia;

    // ---------------------------------------------------------------------
    // Coordinación
    // ---------------------------------------------------------------------

    /**
     * HU 9.1: parte el horario [desde, hasta) de un día en bloques de {@code minutos}.
     * Los que ya existían a la misma hora se omiten. Devuelve cuántos se crearon.
     */
    public int publicarBloques(UUID idCoordinador, LocalDate dia, LocalTime desde, LocalTime hasta, Integer minutos, String lugar) {
        Usuario coord = guardia.exigir(idCoordinador, Permiso.AGENDAR_ATENCION);
        Reglas.requerido(dia, "Elige el día.");
        Reglas.requerido(desde, "Indica la hora de inicio.");
        Reglas.requerido(hasta, "Indica la hora de fin.");
        if (minutos == null || minutos < 10 || minutos > 120) {
            throw new ReglaNegocioException("Cada bloque debe durar entre 10 y 120 minutos.");
        }
        if (!hasta.isAfter(desde)) {
            throw new ReglaNegocioException("La hora de fin debe ser posterior a la de inicio.");
        }
        String sitio = Reglas.texto(lugar, "El lugar", 150);
        OffsetDateTime inicio = dia.atTime(desde).atZone(Fechas.ZONA).toOffsetDateTime();
        OffsetDateTime limite = dia.atTime(hasta).atZone(Fechas.ZONA).toOffsetDateTime();
        if (!inicio.isAfter(Fechas.ahora())) {
            throw new ReglaNegocioException("Los bloques deben empezar en el futuro.");
        }
        List<OffsetDateTime> ocupados = new ArrayList<>();
        for (BloqueAtencion b : agenda.bloquesDe(coord, Fechas.inicio(dia), Fechas.finExclusivo(dia))) {
            ocupados.add(b.getInicio());
        }
        int creados = 0;
        for (OffsetDateTime t = inicio; !t.plusMinutes(minutos).isAfter(limite); t = t.plusMinutes(minutos)) {
            if (creados >= MAX_BLOQUES_POR_DIA) {
                break;
            }
            if (!contieneInstante(ocupados, t)) {
                agenda.guardar(new BloqueAtencion(coord, t, t.plusMinutes(minutos), sitio));
                creados++;
            }
        }
        if (creados == 0) {
            throw new ReglaNegocioException("No se creó ningún bloque: ese horario ya estaba publicado o es demasiado corto.");
        }
        return creados;
    }

    /** HU 9.1 y 9.3: bloques de quien consulta entre dos días, con su cita si está ocupado. */
    public List<EspacioAgenda> misBloques(UUID idCoordinador, LocalDate desde, LocalDate hasta) {
        Usuario coord = guardia.exigir(idCoordinador, Permiso.AGENDAR_ATENCION);
        Reglas.periodo(desde, hasta);
        List<EspacioAgenda> r = new ArrayList<>();
        for (BloqueAtencion b : agenda.bloquesDe(coord, Fechas.inicio(desde), Fechas.finExclusivo(hasta))) {
            r.add(new EspacioAgenda(b, agenda.programadaDe(b).orElse(null)));
        }
        return r;
    }

    /** HU 9.3: citas del día (programadas y ya atendidas), en orden de hora. */
    public List<Cita> agendaDelDia(UUID idCoordinador, LocalDate dia) {
        Usuario coord = guardia.exigir(idCoordinador, Permiso.AGENDAR_ATENCION);
        return agenda.agenda(coord, Fechas.inicio(dia), Fechas.finExclusivo(dia));
    }

    /** HU 9.1: cancela un bloque; si estaba ocupado se cancela la cita y se avisa al alumno. */
    public void cancelarBloque(UUID idCoordinador, UUID idBloque) {
        Usuario coord = guardia.exigir(idCoordinador, Permiso.AGENDAR_ATENCION);
        BloqueAtencion b = agenda.buscar(idBloque).orElseThrow(() -> new NoEncontradoException("El bloque no existe."));
        if (!b.getCoordinador().equals(coord)) {
            throw new AccesoDenegadoException("Ese horario no es tuyo.");
        }
        agenda.programadaDe(b).ifPresent(c -> {
            c.cerrar(EstadoCita.CANCELADA);
            notificaciones.avisar(c.getAlumno(), c.getSolicitud(), "Se canceló tu cita de " + c.getSolicitud().getFolioTexto(),
                    "Coordinación canceló el horario del " + texto(b) + ". Agenda otro desde el detalle de tu solicitud.");
        });
        b.cancelar();
    }

    /** HU 9.3: asistencia o inasistencia. Si asistió, la solicitud ya no requiere cita. */
    public Cita registrarAsistencia(UUID idCoordinador, UUID idCita, boolean asistio) {
        Usuario coord = guardia.exigir(idCoordinador, Permiso.AGENDAR_ATENCION);
        Cita c = citaExistente(idCita);
        if (!c.getBloque().getCoordinador().equals(coord)) {
            throw new AccesoDenegadoException("Esa cita no está en tu agenda.");
        }
        if (c.getBloque().isFuturo()) {
            throw new ReglaNegocioException("La asistencia se registra cuando llega la hora de la cita.");
        }
        c.cerrar(asistio ? EstadoCita.ASISTIO : EstadoCita.NO_ASISTIO);
        if (asistio) {
            c.getSolicitud().setCitaRequerida(false);
        }
        return c;
    }

    // ---------------------------------------------------------------------
    // Alumno
    // ---------------------------------------------------------------------

    /** HU 9.2: horarios libres de la coordinación de su carrera para una solicitud que pide cita. */
    public List<BloqueAtencion> horariosLibres(UUID idAlumno, int folio) {
        Solicitud s = solicitudQuePideCita(idAlumno, folio);
        return agenda.libresPara(s.getCarrera(), Fechas.ahora().plusHours(1));
    }

    /**
     * HU 9.2: agenda (o reprograma) la cita de una solicitud. Si ya tenía una, la anterior se
     * cancela; solo se puede hasta {@link #PLAZO_HORAS} horas antes de esa cita.
     */
    public Cita agendar(UUID idAlumno, int folio, UUID idBloque) {
        Solicitud s = solicitudQuePideCita(idAlumno, folio);
        BloqueAtencion b = agenda.buscar(Reglas.requerido(idBloque, "Elige un horario."))
                .orElseThrow(() -> new NoEncontradoException("El horario no existe."));
        if (b.isCancelado() || !b.isFuturo() || !b.getCoordinador().atiendeCarrera(s.getCarrera())) {
            throw new ReglaNegocioException("Ese horario ya no está disponible.");
        }
        if (agenda.programadaDe(b).isPresent()) {
            throw new ReglaNegocioException("Ese horario se acaba de ocupar. Elige otro.");
        }
        agenda.programadaDe(s).ifPresent(anterior -> {
            exigirDentroDelPlazo(anterior);
            anterior.cerrar(EstadoCita.CANCELADA);
            agenda.sincronizar();
        });
        Cita c = new Cita(b, s, s.getSolicitante());
        agenda.guardarCita(c);
        try {
            agenda.sincronizar();
        } catch (PersistenceException e) {
            if (Reglas.violaRestriccion(e, "uq_cita_bloque")) {
                throw new ReglaNegocioException("Ese horario se acaba de ocupar. Elige otro.");
            }
            throw e;
        }
        notificaciones.avisar(b.getCoordinador(), s, "Cita agendada para " + s.getFolioTexto(),
                s.getSolicitante().getNombreCompleto() + " vendrá el " + texto(b) + " (" + b.getLugar() + ").");
        return c;
    }

    /** HU 9.2: el alumno cancela su cita (hasta el plazo). */
    public void cancelarCita(UUID idAlumno, UUID idCita) {
        Usuario alumno = guardia.exigir(idAlumno, Permiso.SOLICITAR);
        Cita c = citaExistente(idCita);
        if (!c.getAlumno().equals(alumno)) {
            throw new AccesoDenegadoException("Esa cita no es tuya.");
        }
        exigirDentroDelPlazo(c);
        c.cerrar(EstadoCita.CANCELADA);
        notificaciones.avisar(c.getBloque().getCoordinador(), c.getSolicitud(), "Cita cancelada en " + c.getSolicitud().getFolioTexto(),
                alumno.getNombreCompleto() + " canceló la cita del " + texto(c.getBloque()) + ".");
    }

    /** La cita vigente de una solicitud, para quien puede ver la solicitud. */
    public Optional<Cita> citaVigente(UUID idUsuario, int folio) {
        return agenda.programadaDe(solicitudService.detalle(folio, idUsuario));
    }

    // ---------------------------------------------------------------------

    private Solicitud solicitudQuePideCita(UUID idAlumno, int folio) {
        Usuario alumno = guardia.exigir(idAlumno, Permiso.SOLICITAR);
        Solicitud s = solicitudService.existente(folio);
        if (!s.getSolicitante().equals(alumno)) {
            throw new AccesoDenegadoException("Solo quien registró la solicitud agenda su cita.");
        }
        if (!s.getEstado().isAbierto() || !s.isCitaRequerida()) {
            throw new ReglaNegocioException("Esta solicitud no requiere cita.");
        }
        return s;
    }

    private Cita citaExistente(UUID id) {
        return agenda.cita(id).orElseThrow(() -> new NoEncontradoException("La cita no existe."));
    }

    private static void exigirDentroDelPlazo(Cita c) {
        if (!c.isProgramada()) {
            throw new ReglaNegocioException("La cita ya no está programada.");
        }
        if (Duration.between(Fechas.ahora(), c.getBloque().getInicio()).toHours() < PLAZO_HORAS) {
            throw new ReglaNegocioException("Las citas solo se cambian o cancelan hasta " + PLAZO_HORAS + " horas antes.");
        }
    }

    private static boolean contieneInstante(List<OffsetDateTime> lista, OffsetDateTime t) {
        for (OffsetDateTime x : lista) {
            if (x.isEqual(t)) {
                return true;
            }
        }
        return false;
    }

    private static String texto(BloqueAtencion b) {
        return b.getInicio().atZoneSameInstant(Fechas.ZONA).toLocalDate() + " a las "
                + b.getInicio().atZoneSameInstant(Fechas.ZONA).toLocalTime();
    }
}
