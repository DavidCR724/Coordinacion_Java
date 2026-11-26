package acceso.datos;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

/**
 * «acceso a datos» Operaciones comunes de los repositorios.
 * Cada repositorio concreto es un EJB @Stateless: se une a la transacción del servicio que lo llama.
 */
public abstract class RepositorioBase<T, K> {

    @PersistenceContext(unitName = "CoordinadITOPU")
    protected EntityManager em;

    private final Class<T> tipo;

    protected RepositorioBase(Class<T> tipo) {
        this.tipo = tipo;
    }

    public Optional<T> buscar(K id) {
        return id == null ? Optional.<T>empty() : Optional.ofNullable(em.find(tipo, id));
    }

    public void guardar(T entidad) {
        em.persist(entidad);
    }

    public T actualizar(T entidad) {
        return em.merge(entidad);
    }

    public List<T> todos() {
        return em.createQuery("SELECT e FROM " + tipo.getSimpleName() + " e", tipo).getResultList();
    }

    /** Envía los cambios pendientes a la base ya (por ejemplo, para que se disparen sus restricciones). */
    public void sincronizar() {
        em.flush();
    }

    /** La entidad cuyo campo vale exactamente {@code valor} (llaves naturales: clave, folio, correo...). */
    protected Optional<T> unoPor(String campo, Object valor) {
        return primero(em.createQuery("SELECT e FROM " + tipo.getSimpleName() + " e WHERE e." + campo + " = :v", tipo)
                .setParameter("v", valor).getResultList());
    }

    protected static <R> Optional<R> primero(List<R> lista) {
        return lista.isEmpty() ? Optional.<R>empty() : Optional.of(lista.get(0));
    }
}
