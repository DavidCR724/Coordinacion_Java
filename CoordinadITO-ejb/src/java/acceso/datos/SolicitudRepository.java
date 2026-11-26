package acceso.datos;

import jakarta.ejb.Stateless;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import modelo.entity.Carrera;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Materia;
import modelo.entity.Prioridad;
import modelo.entity.Solicitud;
import modelo.entity.Tramite;
import modelo.entity.Usuario;

/** «acceso a datos» Solicitudes: registro, consulta, bandeja, grupos y seguimiento. */
@Stateless
public class SolicitudRepository extends RepositorioBase<Solicitud, UUID> {

    private static final String DE_BANDEJA = " FROM Solicitud s JOIN s.solicitante a";

    public SolicitudRepository() {
        super(Solicitud.class);
    }

    public Optional<Solicitud> porFolio(int folio) {
        return unoPor("folio", folio);
    }

    /** Recarga la solicitud desde la base: trae el FOLIO que genera PostgreSQL. */
    public void recargar(Solicitud s) {
        em.flush();
        em.refresh(s);
    }

    /**
     * Mis solicitudes (HU 2.3 y 4.3): las que registró el alumno y las aperturas de grupo a las que
     * se adhirió, de la más reciente a la más antigua.
     */
    public List<Solicitud> deAlumno(Usuario alumno) {
        return em.createQuery("SELECT s FROM Solicitud s WHERE s.solicitante = :a "
                + "OR EXISTS (SELECT 1 FROM Adhesion x WHERE x.solicitud = s AND x.alumno = :a) "
                + "ORDER BY s.fechaRegistro DESC", Solicitud.class)
                .setParameter("a", alumno).getResultList();
    }

    /** Solicitud abierta del mismo alumno y trámite (HU 2.9). */
    public Optional<Solicitud> abiertaDe(Usuario alumno, Tramite tramite) {
        return primero(em.createQuery("SELECT s FROM Solicitud s WHERE s.solicitante = :a AND s.tramite = :t "
                + "AND s.materia IS NULL AND s.estado IN :abiertos", Solicitud.class)
                .setParameter("a", alumno).setParameter("t", tramite)
                .setParameter("abiertos", EstadoSolicitud.abiertos())
                .getResultList());
    }

    // ---------------------------------------------------------------------
    // Bandeja (HU 2.1, 3.1, 3.4)
    // ---------------------------------------------------------------------

    /**
     * @param carreras null = todas (jefatura); vacío = ninguna
     * @param estados  null o vacío = abiertas
     * @param porFecha false (normal): prioridad más alta primero y, dentro de la misma prioridad,
     *                 la más antigua primero (HU 3.1). true: solo por antigüedad (HU 3.1, criterio 3).
     */
    public List<Solicitud> bandeja(Set<Carrera> carreras, Set<EstadoSolicitud> estados, UUID idTramite,
                                   Prioridad prioridadMinima, OffsetDateTime desde, OffsetDateTime hasta,
                                   String texto, boolean porFecha, int inicio, int maximo) {
        return filtroBandeja(carreras, estados, idTramite, prioridadMinima, desde, hasta, texto)
                .lista(em, "SELECT s" + DE_BANDEJA,
                        porFecha ? "ORDER BY s.fechaRegistro ASC, s.folio ASC" : "ORDER BY s.prioridad DESC, s.fechaRegistro ASC",
                        Solicitud.class, inicio, maximo);
    }

    public long contarBandeja(Set<Carrera> carreras, Set<EstadoSolicitud> estados, UUID idTramite,
                              Prioridad prioridadMinima, OffsetDateTime desde, OffsetDateTime hasta, String texto) {
        return filtroBandeja(carreras, estados, idTramite, prioridadMinima, desde, hasta, texto)
                .contar(em, "SELECT COUNT(s)" + DE_BANDEJA);
    }

    private static Consulta filtroBandeja(Set<Carrera> carreras, Set<EstadoSolicitud> estados, UUID idTramite,
                                          Prioridad prioridadMinima, OffsetDateTime desde, OffsetDateTime hasta, String texto) {
        return new Consulta()
                .y("s.estado IN :estados", "estados", estados == null || estados.isEmpty() ? EstadoSolicitud.abiertos() : estados)
                .dentroDe("s.carrera", carreras)
                .siHay("s.tramite.id = :tramite", "tramite", idTramite)
                .siHay("s.prioridad IN :prioridades", "prioridades", prioridadMinima == null ? null : desdePrioridad(prioridadMinima))
                .siHay("s.fechaRegistro >= :desde", "desde", desde)
                .siHay("s.fechaRegistro < :hasta", "hasta", hasta)
                .texto(texto, "s.folio", "a.nombres", "a.apellidoPaterno", "a.numControl", "a.correo");
    }

    /** Contadores por estado para el encabezado de la bandeja. */
    public Map<EstadoSolicitud, Long> contarPorEstado(Set<Carrera> carreras) {
        Map<EstadoSolicitud, Long> r = new EnumMap<>(EstadoSolicitud.class);
        for (EstadoSolicitud e : EstadoSolicitud.values()) {
            r.put(e, 0L);
        }
        for (Object[] fila : new Consulta().dentroDe("s.carrera", carreras)
                .lista(em, "SELECT s.estado, COUNT(s) FROM Solicitud s", "GROUP BY s.estado", Object[].class)) {
            r.put((EstadoSolicitud) fila[0], ((Number) fila[1]).longValue());
        }
        return r;
    }

    /** Cuántas solicitudes abiertas hay por prioridad (para la tarjeta «prioridad alta»). */
    public Map<Prioridad, Long> abiertasPorPrioridad(Set<Carrera> carreras) {
        Map<Prioridad, Long> r = new HashMap<>();
        for (Object[] fila : new Consulta().y("s.estado IN :abiertos", "abiertos", EstadoSolicitud.abiertos())
                .dentroDe("s.carrera", carreras)
                .lista(em, "SELECT s.prioridad, COUNT(s) FROM Solicitud s", "GROUP BY s.prioridad", Object[].class)) {
            r.put((Prioridad) fila[0], ((Number) fila[1]).longValue());
        }
        return r;
    }

    /** Solicitudes abiertas por trámite (HU 3.2: jefatura ve cuántas conservan su prioridad). */
    public Map<UUID, Long> abiertasPorTramite() {
        Map<UUID, Long> r = new HashMap<>();
        for (Object[] fila : new Consulta().y("s.estado IN :abiertos", "abiertos", EstadoSolicitud.abiertos())
                .lista(em, "SELECT s.tramite.id, COUNT(s) FROM Solicitud s", "GROUP BY s.tramite.id", Object[].class)) {
            r.put((UUID) fila[0], ((Number) fila[1]).longValue());
        }
        return r;
    }

    // ---------------------------------------------------------------------
    // Apertura de grupos (HU 4.x)
    // ---------------------------------------------------------------------

    /** La solicitud de apertura activa de una materia (solo puede haber una, índice UQ_GRUPO_ABIERTO_MATERIA). */
    public Optional<Solicitud> grupoAbiertoDe(Materia materia) {
        return primero(em.createQuery("SELECT s FROM Solicitud s WHERE s.materia = :m AND s.estado IN :abiertos", Solicitud.class)
                .setParameter("m", materia).setParameter("abiertos", EstadoSolicitud.abiertos()).getResultList());
    }

    /** Solicitudes de apertura de las carreras indicadas (null = todas), de la más antigua a la más reciente. */
    public List<Solicitud> grupos(Set<Carrera> carreras, Set<EstadoSolicitud> estados, String texto) {
        return new Consulta()
                .dentroDe("m.carrera", carreras)
                .siHay("s.estado IN :estados", "estados", estados)
                .texto(texto, "s.folio", "m.nombre", "m.clave")
                .lista(em, "SELECT s FROM Solicitud s JOIN s.materia m", "ORDER BY s.fechaRegistro ASC", Solicitud.class);
    }

    /** Adhesiones por solicitud, en una sola consulta (evita contar una por una). */
    public Map<UUID, Long> adhesionesPor(List<Solicitud> solicitudes) {
        Map<UUID, Long> r = new HashMap<>();
        if (solicitudes.isEmpty()) {
            return r;
        }
        for (Object[] fila : em.createQuery("SELECT x.solicitud.id, COUNT(x) FROM Adhesion x WHERE x.solicitud IN :ss "
                + "GROUP BY x.solicitud.id", Object[].class).setParameter("ss", solicitudes).getResultList()) {
            r.put((UUID) fila[0], ((Number) fila[1]).longValue());
        }
        return r;
    }

    /** Ids de las aperturas a las que el alumno está adherido. */
    public Set<UUID> adheridasPor(Usuario alumno) {
        return new java.util.HashSet<>(em.createQuery("SELECT x.solicitud.id FROM Adhesion x WHERE x.alumno = :a", UUID.class)
                .setParameter("a", alumno).getResultList());
    }

    // ---------------------------------------------------------------------
    // Seguimiento (HU 7.x)
    // ---------------------------------------------------------------------

    /** Solicitudes registradas en [desde, hasta), por folio. Base de volumen, tiempos y exportación. */
    public List<Solicitud> registradasEntre(OffsetDateTime desde, OffsetDateTime hasta) {
        return em.createQuery("SELECT s FROM Solicitud s WHERE s.fechaRegistro >= :desde AND s.fechaRegistro < :hasta "
                + "ORDER BY s.folio", Solicitud.class)
                .setParameter("desde", desde).setParameter("hasta", hasta).getResultList();
    }

    /** Abiertas de las carreras (null = todas), de la más antigua a la más reciente (HU 7.3). */
    public List<Solicitud> abiertas(Set<Carrera> carreras) {
        return new Consulta().y("s.estado IN :abiertos", "abiertos", EstadoSolicitud.abiertos())
                .dentroDe("s.carrera", carreras)
                .lista(em, "SELECT s FROM Solicitud s", "ORDER BY s.fechaRegistro ASC", Solicitud.class);
    }

    private static List<Prioridad> desdePrioridad(Prioridad minima) {
        List<Prioridad> ps = new ArrayList<>();
        for (Prioridad p : Prioridad.values()) {
            if (p.compareTo(minima) >= 0) {
                ps.add(p);
            }
        }
        return ps;
    }
}
