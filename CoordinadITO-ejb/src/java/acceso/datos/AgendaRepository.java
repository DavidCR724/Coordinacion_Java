package acceso.datos;

import jakarta.ejb.Stateless;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import modelo.entity.BloqueAtencion;
import modelo.entity.Carrera;
import modelo.entity.Cita;
import modelo.entity.EstadoCita;
import modelo.entity.Rol;
import modelo.entity.Solicitud;
import modelo.entity.Usuario;

/** «acceso a datos» Bloques de atención y citas (HU 9.1 a 9.3). */
@Stateless
public class AgendaRepository extends RepositorioBase<BloqueAtencion, UUID> {

    public AgendaRepository() {
        super(BloqueAtencion.class);
    }

    /** Bloques vigentes de una persona en un rango de fechas. */
    public List<BloqueAtencion> bloquesDe(Usuario coordinador, OffsetDateTime desde, OffsetDateTime hasta) {
        return em.createQuery("SELECT b FROM BloqueAtencion b WHERE b.coordinador = :c AND b.cancelado = FALSE "
                + "AND b.inicio >= :desde AND b.inicio < :hasta ORDER BY b.inicio", BloqueAtencion.class)
                .setParameter("c", coordinador).setParameter("desde", desde).setParameter("hasta", hasta).getResultList();
    }

    /**
     * HU 9.2: bloques libres a partir de {@code desde} de quien atiende la carrera del alumno.
     * Un bloque con cita programada deja de ofrecerse.
     */
    public List<BloqueAtencion> libresPara(Carrera carrera, OffsetDateTime desde) {
        return em.createQuery("SELECT b FROM BloqueAtencion b JOIN b.coordinador u JOIN u.carrerasAsignadas k "
                + "WHERE k = :carrera AND u.rol = :rol AND u.activo = TRUE AND b.cancelado = FALSE AND b.inicio > :desde "
                + "AND NOT EXISTS (SELECT 1 FROM Cita c WHERE c.bloque = b AND c.estado = :programada) "
                + "ORDER BY b.inicio", BloqueAtencion.class)
                .setParameter("carrera", carrera).setParameter("rol", Rol.COORDINADOR)
                .setParameter("desde", desde).setParameter("programada", EstadoCita.PROGRAMADA)
                .setMaxResults(200).getResultList();
    }

    public void guardarCita(Cita cita) {
        em.persist(cita);
    }

    public Optional<Cita> cita(UUID id) {
        return Optional.ofNullable(em.find(Cita.class, id));
    }

    /** La cita programada de un bloque o de una solicitud, si la hay. */
    public Optional<Cita> programadaDe(BloqueAtencion bloque) {
        return primero(em.createQuery("SELECT c FROM Cita c WHERE c.bloque = :b AND c.estado = :p", Cita.class)
                .setParameter("b", bloque).setParameter("p", EstadoCita.PROGRAMADA).getResultList());
    }

    public Optional<Cita> programadaDe(Solicitud solicitud) {
        return primero(em.createQuery("SELECT c FROM Cita c WHERE c.solicitud = :s AND c.estado = :p", Cita.class)
                .setParameter("s", solicitud).setParameter("p", EstadoCita.PROGRAMADA).getResultList());
    }

    /** Historial de citas de una solicitud (la más reciente primero). */
    public List<Cita> citasDe(Solicitud solicitud) {
        return em.createQuery("SELECT c FROM Cita c WHERE c.solicitud = :s ORDER BY c.fechaRegistro DESC", Cita.class)
                .setParameter("s", solicitud).getResultList();
    }

    /** HU 9.3: citas (no canceladas) de una persona de coordinación entre dos momentos. */
    public List<Cita> agenda(Usuario coordinador, OffsetDateTime desde, OffsetDateTime hasta) {
        return em.createQuery("SELECT c FROM Cita c JOIN c.bloque b WHERE b.coordinador = :u AND b.inicio >= :desde "
                + "AND b.inicio < :hasta AND c.estado <> :cancelada ORDER BY b.inicio", Cita.class)
                .setParameter("u", coordinador).setParameter("desde", desde).setParameter("hasta", hasta)
                .setParameter("cancelada", EstadoCita.CANCELADA).getResultList();
    }
}
