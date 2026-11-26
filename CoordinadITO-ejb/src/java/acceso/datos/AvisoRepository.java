package acceso.datos;

import jakarta.ejb.Stateless;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import modelo.entity.Aviso;
import modelo.entity.Carrera;

/** «acceso a datos» Avisos oficiales (HU 5.3). */
@Stateless
public class AvisoRepository extends RepositorioBase<Aviso, UUID> {

    public AvisoRepository() {
        super(Aviso.class);
    }

    /**
     * Avisos dirigidos a alguna de las carreras (null = todas), del más reciente al más antiguo.
     * @param soloVigentesEn si no es null, solo los vigentes ese día
     */
    public List<Aviso> para(Set<Carrera> carreras, LocalDate soloVigentesEn, int maximo) {
        Consulta c = new Consulta().dentroDe("k", carreras);
        if (soloVigentesEn != null) {
            c.y("a.inicioVigencia <= :dia AND a.finVigencia >= :dia", "dia", soloVigentesEn);
        }
        return c.lista(em, "SELECT DISTINCT a FROM Aviso a JOIN a.carreras k", "ORDER BY a.fechaPublicacion DESC",
                Aviso.class, 0, maximo);
    }

    /** De estos avisos, cuáles ya leyó la persona. */
    @SuppressWarnings("unchecked")
    public Set<UUID> leidosPor(UUID idUsuario, Collection<UUID> idsAvisos) {
        Set<UUID> r = new HashSet<>();
        if (idsAvisos.isEmpty()) {
            return r;
        }
        List<Object> ids = em.createNativeQuery(
                "SELECT ID_AVISO FROM AVISO_LECTURA WHERE ID_USUARIO = ?1 AND ID_AVISO = ANY (CAST(?2 AS UUID[]))")
                .setParameter(1, idUsuario)
                .setParameter(2, "{" + String.join(",", idsAvisos.stream().map(UUID::toString).toArray(String[]::new)) + "}")
                .getResultList();
        for (Object id : ids) {
            r.add(id instanceof UUID ? (UUID) id : UUID.fromString(id.toString()));
        }
        return r;
    }

    /** Registra que la persona leyó el aviso (si ya lo había leído, no cambia nada). */
    public void marcarLeido(UUID idUsuario, UUID idAviso) {
        em.createNativeQuery("INSERT INTO AVISO_LECTURA (ID_AVISO, ID_USUARIO) VALUES (?1, ?2) ON CONFLICT DO NOTHING")
                .setParameter(1, idAviso)
                .setParameter(2, idUsuario)
                .executeUpdate();
    }
}
