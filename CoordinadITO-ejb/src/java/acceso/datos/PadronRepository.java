package acceso.datos;

import jakarta.ejb.Stateless;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import modelo.entity.CargaPadron;
import modelo.entity.Carrera;
import modelo.entity.PadronAlumno;
import modelo.entity.Usuario;

/** «acceso a datos» Padrón institucional y sus cargas (HU 6.4, 8.1). */
@Stateless
public class PadronRepository extends RepositorioBase<PadronAlumno, String> {

    public PadronRepository() {
        super(PadronAlumno.class);
    }

    /** Alumno vigente del padrón por su correo institucional (para el primer acceso). */
    public Optional<PadronAlumno> vigentePorCorreo(String correo) {
        return primero(em.createQuery("SELECT p FROM PadronAlumno p WHERE LOWER(p.correo) = :c AND p.vigente = TRUE",
                PadronAlumno.class).setParameter("c", correo.trim().toLowerCase()).getResultList());
    }

    public Optional<PadronAlumno> unoPorCorreo(String correo) {
        return unoPor("correo", correo);
    }

    /** HU 8.1: quien no viene en la carga nueva deja de estar vigente. */
    /**
     * Marca como no vigentes a quienes no vinieron en el archivo, solo dentro de las carreras dadas
     * (null = todas): el archivo de una coordinación no retira a alumnos de otras carreras.
     */
    public int retirarAusentes(Collection<String> presentes, Collection<Carrera> carreras) {
        if (carreras != null && carreras.isEmpty()) {
            return 0;
        }
        Query q = em.createQuery("UPDATE PadronAlumno p SET p.vigente = FALSE WHERE p.vigente = TRUE AND p.numControl NOT IN :presentes"
                + (carreras == null ? "" : " AND p.carrera IN :carreras"))
                .setParameter("presentes", presentes);
        if (carreras != null) {
            q.setParameter("carreras", carreras);
        }
        return q.executeUpdate();
    }

    public void guardarCarga(CargaPadron carga) {
        em.persist(carga);
    }

    /** Cargas recientes; con responsable, solo las que subió esa persona. */
    public List<CargaPadron> cargas(Usuario responsable, int maximo) {
        TypedQuery<CargaPadron> q = em.createQuery("SELECT c FROM CargaPadron c"
                + (responsable == null ? "" : " WHERE c.responsable = :r") + " ORDER BY c.fecha DESC", CargaPadron.class);
        if (responsable != null) {
            q.setParameter("r", responsable);
        }
        return q.setMaxResults(maximo).getResultList();
    }

    /** Alumnos vigentes en el padrón de esas carreras (null = todas). */
    public long vigentes(Collection<Carrera> carreras) {
        if (carreras != null && carreras.isEmpty()) {
            return 0;
        }
        TypedQuery<Long> q = em.createQuery("SELECT COUNT(p) FROM PadronAlumno p WHERE p.vigente = TRUE"
                + (carreras == null ? "" : " AND p.carrera IN :carreras"), Long.class);
        if (carreras != null) {
            q.setParameter("carreras", carreras);
        }
        return q.getSingleResult();
    }
}
