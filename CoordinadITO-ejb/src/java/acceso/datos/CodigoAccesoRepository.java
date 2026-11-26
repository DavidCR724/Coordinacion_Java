package acceso.datos;

import jakarta.ejb.Stateless;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import modelo.entity.CodigoAcceso;

/** «acceso a datos» Códigos de acceso por correo (tabla CODIGO_ACCESO). */
@Stateless
public class CodigoAccesoRepository extends RepositorioBase<CodigoAcceso, UUID> {

    public CodigoAccesoRepository() {
        super(CodigoAcceso.class);
    }

    /** El último código enviado a ese correo (solo el último sirve: pedir otro invalida los anteriores). */
    public Optional<CodigoAcceso> ultimo(String correo) {
        return primero(em.createQuery(
                "SELECT c FROM CodigoAcceso c WHERE c.correo = :correo ORDER BY c.creadoEn DESC", CodigoAcceso.class)
                .setParameter("correo", correo)
                .setMaxResults(1)
                .getResultList());
    }

    /** Cuántos códigos se enviaron a ese correo desde ese momento. */
    public long enviadosA(String correo, OffsetDateTime desde) {
        return em.createQuery(
                "SELECT COUNT(c) FROM CodigoAcceso c WHERE c.correo = :correo AND c.creadoEn >= :desde", Long.class)
                .setParameter("correo", correo)
                .setParameter("desde", desde)
                .getSingleResult();
    }

    /** Cuántos códigos se pidieron desde esa dirección IP desde ese momento. */
    public long enviadosDesde(String ip, OffsetDateTime desde) {
        return em.createQuery(
                "SELECT COUNT(c) FROM CodigoAcceso c WHERE c.ipOrigen = :ip AND c.creadoEn >= :desde", Long.class)
                .setParameter("ip", ip)
                .setParameter("desde", desde)
                .getSingleResult();
    }
}
