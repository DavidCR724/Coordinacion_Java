package acceso.datos;

import jakarta.ejb.Stateless;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import modelo.entity.Carrera;
import modelo.entity.Rol;
import modelo.entity.Usuario;

/** «acceso a datos» Usuarios: alumnos y personal. */
@Stateless
public class UsuarioRepository extends RepositorioBase<Usuario, UUID> {

    public UsuarioRepository() {
        super(Usuario.class);
    }

    /** Búsqueda sin distinguir mayúsculas (la base tiene un índice único sobre LOWER(CORREO)). */
    public Optional<Usuario> porCorreo(String correo) {
        if (correo == null) {
            return Optional.empty();
        }
        return primero(em.createQuery("SELECT u FROM Usuario u WHERE LOWER(u.correo) = :correo", Usuario.class)
                .setParameter("correo", correo.trim().toLowerCase()).getResultList());
    }

    public Optional<Usuario> porNumControl(String numControl) {
        return unoPor("numControl", numControl);
    }

    /** Coordinación activa que atiende una carrera (para avisarle de solicitudes nuevas). */
    public List<Usuario> coordinadoresDe(Carrera carrera) {
        return em.createQuery("SELECT u FROM Usuario u JOIN u.carrerasAsignadas c "
                + "WHERE c = :carrera AND u.rol = :rol AND u.activo = TRUE", Usuario.class)
                .setParameter("carrera", carrera)
                .setParameter("rol", Rol.COORDINADOR)
                .getResultList();
    }

    /** HU 6.3: cuentas de personal (todo lo que no es estudiante), por rol y nombre. */
    public List<Usuario> personal() {
        return em.createQuery("SELECT u FROM Usuario u WHERE u.rol <> :estudiante ORDER BY u.rol, u.nombres, u.apellidoPaterno",
                Usuario.class).setParameter("estudiante", Rol.ESTUDIANTE).getResultList();
    }

    /** HU 6.5: alumnos de las carreras (null = todas) que coinciden con el texto. */
    public List<Usuario> alumnos(Set<Carrera> carreras, UUID idCarrera, String texto, int inicio, int maximo) {
        return filtroAlumnos(carreras, idCarrera, texto)
                .lista(em, "SELECT u FROM Usuario u", "ORDER BY u.apellidoPaterno, u.nombres", Usuario.class, inicio, maximo);
    }

    public long contarAlumnos(Set<Carrera> carreras, UUID idCarrera, String texto) {
        return filtroAlumnos(carreras, idCarrera, texto).contar(em, "SELECT COUNT(u) FROM Usuario u");
    }

    private static Consulta filtroAlumnos(Set<Carrera> carreras, UUID idCarrera, String texto) {
        return new Consulta()
                .y("u.rol = :estudiante", "estudiante", Rol.ESTUDIANTE)
                .dentroDe("u.carrera", carreras)
                .siHay("u.carrera.id = :carrera", "carrera", idCarrera)
                .texto(texto, null, "u.nombres", "u.apellidoPaterno", "u.apellidoMaterno", "u.numControl", "u.correo");
    }
}
