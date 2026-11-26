package modelo.bean;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import acceso.datos.UsuarioRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.util.UUID;
import modelo.entity.Carrera;
import modelo.entity.Usuario;

/**
 * «servicio» Portero de las operaciones. Recarga al usuario de la base en cada llamada:
 * los permisos se deciden con datos frescos (rol, carreras, cuenta activa), nunca con la copia
 * que guarda la sesión del WAR.
 */
@Stateless
public class Guardia {

    @EJB
    private UsuarioRepository usuarios;

    /** Usuario activo, sin importar su rol. */
    public Usuario usuario(UUID id) {
        Usuario u = usuarios.buscar(id).orElseThrow(() -> new AccesoDenegadoException("Sesión no válida."));
        if (!u.isActivo()) {
            throw new AccesoDenegadoException("Tu cuenta está desactivada. Comunícate con la coordinación.");
        }
        return u;
    }

    /** Usuario activo cuyo rol tiene el permiso. */
    public Usuario exigir(UUID id, Permiso permiso) {
        Usuario u = usuario(id);
        if (!permiso.permite(u.getRol())) {
            throw new AccesoDenegadoException(permiso.getMensajeNegado());
        }
        return u;
    }

    /**
     * Carreras sobre las que actúa: null = todas (jefatura); si no, las asignadas a la coordinación
     * (copia: quien la reciba puede usarla en una consulta sin tocar la entidad).
     */
    public Set<Carrera> alcance(Usuario actor) {
        return actor.esJefe() ? null : new HashSet<>(actor.getCarrerasAsignadas());
    }

    /** Todas las carreras dadas deben ser del actor (jefatura: siempre). */
    public void exigirCarreras(Usuario actor, Collection<Carrera> carreras) {
        for (Carrera c : carreras) {
            exigirCarrera(actor, c);
        }
    }

    /** Coordinación solo actúa sobre sus carreras; jefatura sobre todas. */
    public void exigirCarrera(Usuario actor, Carrera carrera) {
        if (!actor.atiendeCarrera(carrera)) {
            throw new AccesoDenegadoException("Es de una carrera que no atiendes.");
        }
    }
}
