package acceso.datos;

import jakarta.ejb.Stateless;
import java.util.List;
import java.util.UUID;
import modelo.entity.Carrera;
import modelo.entity.Materia;

/** «acceso a datos» Materias para la apertura de grupos (HU 4.1). */
@Stateless
public class MateriaRepository extends RepositorioBase<Materia, UUID> {

    public MateriaRepository() {
        super(Materia.class);
    }

    /** Materias activas de una carrera, por semestre y nombre. */
    public List<Materia> deCarrera(Carrera carrera) {
        return em.createQuery("SELECT m FROM Materia m WHERE m.carrera = :c AND m.activa = TRUE "
                + "ORDER BY m.semestre, m.nombre", Materia.class).setParameter("c", carrera).getResultList();
    }
}
