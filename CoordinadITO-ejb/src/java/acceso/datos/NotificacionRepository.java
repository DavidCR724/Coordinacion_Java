package acceso.datos;

import jakarta.ejb.Stateless;
import java.util.List;
import java.util.UUID;
import modelo.entity.Notificacion;
import modelo.entity.Usuario;

/** «acceso a datos» Notificaciones (HU 5.1, 5.2). */
@Stateless
public class NotificacionRepository extends RepositorioBase<Notificacion, UUID> {

    public NotificacionRepository() {
        super(Notificacion.class);
    }

    public List<Notificacion> de(Usuario usuario, int maximo) {
        return em.createQuery("SELECT n FROM Notificacion n WHERE n.destinatario = :u ORDER BY n.fecha DESC", Notificacion.class)
                .setParameter("u", usuario).setMaxResults(maximo).getResultList();
    }

    public long sinLeer(Usuario usuario) {
        return em.createQuery("SELECT COUNT(n) FROM Notificacion n WHERE n.destinatario = :u AND n.leidaEn IS NULL", Long.class)
                .setParameter("u", usuario).getSingleResult();
    }

    /**
     * Toma hasta {@code maximo} notificaciones sin correo y las bloquea para esta transacción.
     * SKIP LOCKED evita que las dos PCs (dos Payara contra la misma base) manden el mismo correo.
     */
    @SuppressWarnings("unchecked")
    public List<Notificacion> tomarPendientesDeCorreo(int maximo) {
        return em.createNativeQuery("SELECT * FROM NOTIFICACION WHERE CORREO_ENVIADO_EN IS NULL "
                + "ORDER BY FECHA LIMIT ?1 FOR UPDATE SKIP LOCKED", Notificacion.class)
                .setParameter(1, maximo).getResultList();
    }
}
