package acceso.datos;

import jakarta.ejb.Stateless;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import modelo.entity.Tramite;

/** «acceso a datos» Trámites del catálogo y sus campos. */
@Stateless
public class TramiteRepository extends RepositorioBase<Tramite, UUID> {

    public TramiteRepository() {
        super(Tramite.class);
    }

    /** Catálogo que ve el alumno (HU 1.1): solo activos, por nombre. */
    public List<Tramite> activos() {
        return em.createQuery("SELECT t FROM Tramite t WHERE t.activo = TRUE ORDER BY t.nombre", Tramite.class)
                .getResultList();
    }

    /** Todos, para jefatura (HU 1.3, 3.2). */
    public List<Tramite> todosOrdenados() {
        return em.createQuery("SELECT t FROM Tramite t ORDER BY t.nombre", Tramite.class).getResultList();
    }

    public Optional<Tramite> porClave(String clave) {
        return unoPor("clave", clave);
    }

    /** HU 4.1: el trámite colectivo con el que se piden aperturas de grupo. */
    public Optional<Tramite> colectivo() {
        return primero(em.createQuery("SELECT t FROM Tramite t WHERE t.colectivo = TRUE AND t.activo = TRUE ORDER BY t.clave", Tramite.class)
                .getResultList());
    }

    /** HU 8.3: un trámite con solicitudes no se borra, solo se desactiva. */
    public boolean tieneSolicitudes(Tramite t) {
        return em.createQuery("SELECT COUNT(s) FROM Solicitud s WHERE s.tramite = :t", Long.class)
                .setParameter("t", t).getSingleResult() > 0;
    }

    public void borrar(Tramite t) {
        em.remove(t);
    }

    /** Trámite con su formulario cargado (HU 1.2). */
    public Optional<Tramite> conCampos(UUID id) {
        // Un JOIN FETCH no respeta @OrderBy en EclipseLink; se carga la colección por separado
        // para que los campos lleguen en el orden del formulario.
        Optional<Tramite> t = buscar(id);
        t.ifPresent(x -> x.getCampos().size());
        return t;
    }
}
