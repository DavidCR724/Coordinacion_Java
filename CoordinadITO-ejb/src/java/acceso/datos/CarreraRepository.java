package acceso.datos;

import jakarta.ejb.Stateless;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import modelo.entity.Carrera;

/** «acceso a datos» Carreras. */
@Stateless
public class CarreraRepository extends RepositorioBase<Carrera, UUID> {

    public CarreraRepository() {
        super(Carrera.class);
    }

    public Optional<Carrera> porClave(String clave) {
        return unoPor("clave", clave);
    }

    public List<Carrera> ordenadas() {
        return em.createQuery("SELECT c FROM Carrera c ORDER BY c.nombre", Carrera.class).getResultList();
    }
}
