package modelo.bean;

import acceso.datos.UsuarioRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.util.List;
import java.util.HashSet;
import java.util.Collections;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import modelo.entity.Accion;
import modelo.entity.Carrera;
import modelo.entity.Rol;
import modelo.entity.Usuario;

/**
 * «servicio» Cuentas de personal y sus roles (HU 6.3). Jefatura: todo el personal.
 * Coordinación: solo cuentas de sus carreras, y solo puede otorgar el rol de coordinación.
 * El rol se otorga de forma explícita; nunca se deduce del correo. Cada alta, cambio y baja
 * queda en la auditoría con el valor anterior y el nuevo.
 */
@Stateless
public class CuentaService {

    private static final String ENTIDAD = "USUARIO";

    @EJB private UsuarioRepository usuarios;
    @EJB private CatalogoService catalogo;
    @EJB private Guardia guardia;
    @EJB private AuditoriaService auditoria;

    /**
     * HU 6.3: el personal que el actor puede ver. Jefatura: todo el personal. Coordinación: solo
     * las coordinaciones que comparten alguna de sus carreras (jefatura no le aparece).
     */
    public List<Usuario> personal(UUID idActor) {
        Usuario actor = guardia.exigir(idActor, Permiso.GESTIONAR_CARRERA);
        Set<Carrera> alcance = guardia.alcance(actor);
        List<Usuario> r = new ArrayList<>();
        for (Usuario u : usuarios.personal()) {
            if (alcance == null || (u.esCoordinador() && !Collections.disjoint(u.getCarrerasAsignadas(), alcance))) {
                r.add(u);
            }
        }
        return r;
    }

    /** Roles que el actor puede otorgar: jefatura, cualquiera del personal; coordinación, solo coordinación. */
    public List<Rol> rolesQuePuedeAsignar(UUID idActor) {
        Usuario actor = guardia.exigir(idActor, Permiso.GESTIONAR_CARRERA);
        return actor.esJefe() ? Arrays.asList(Rol.COORDINADOR, Rol.JEFE) : Collections.singletonList(Rol.COORDINADOR);
    }

    /** Carreras que el actor puede asignar a una coordinación: jefatura, todas; coordinación, las suyas. */
    public List<Carrera> carrerasQuePuedeAsignar(UUID idActor) {
        Usuario actor = guardia.exigir(idActor, Permiso.GESTIONAR_CARRERA);
        Set<Carrera> alcance = guardia.alcance(actor);
        List<Carrera> r = new ArrayList<>();
        for (Carrera c : catalogo.carreras()) {
            if (alcance == null || alcance.contains(c)) {
                r.add(c);
            }
        }
        return r;
    }

    public Usuario crearPersonal(UUID idActor, String correo, String nombres, String apellidoPaterno,
                                 String apellidoMaterno, Rol rol, Set<UUID> idsCarreras) {
        Usuario actor = guardia.exigir(idActor, Permiso.GESTIONAR_CARRERA);
        String c = Reglas.texto(correo, "El correo", 150).toLowerCase();
        if (!c.endsWith("@" + AccesoService.DOMINIO)) {
            throw new ReglaNegocioException("El correo debe ser institucional (@" + AccesoService.DOMINIO + ").");
        }
        if (usuarios.porCorreo(c).isPresent()) {
            throw new ReglaNegocioException("Ya existe una cuenta con el correo " + c + ".");
        }
        Set<Carrera> carreras = carrerasPara(actor, rol, idsCarreras);
        Usuario u = Usuario.personal(c, Reglas.texto(nombres, "El nombre", 100), Reglas.texto(apellidoPaterno, "El apellido paterno", 100),
                Reglas.opcional(apellidoMaterno, "El apellido materno", 100), rol, carreras);
        usuarios.guardar(u);
        auditoria.registrar(actor, Accion.ALTA_CUENTA, ENTIDAD, u.getId(), "correo", c, "rol", rol, "carreras", claves(carreras));
        return u;
    }

    /** Cambia rol y carreras. Nadie cambia su propio rol: se quedaría sin acceso o se daría más. */
    public Usuario cambiarRol(UUID idActor, UUID idCuenta, Rol rol, Set<UUID> idsCarreras) {
        Usuario actor = guardia.exigir(idActor, Permiso.GESTIONAR_CARRERA);
        Usuario u = personalExistente(idCuenta);
        if (u.equals(actor)) {
            throw new ReglaNegocioException("No puedes cambiar tu propio rol; pídelo a jefatura.");
        }
        exigirACargo(actor, u);
        String antes = u.getRol() + " " + claves(u.getCarrerasAsignadas());
        Set<Carrera> carreras = carrerasPara(actor, rol, idsCarreras);
        u.asignarRol(rol, carreras);
        auditoria.registrar(actor, Accion.CAMBIO_ROL, ENTIDAD, u.getId(), "correo", u.getCorreo(),
                "antes", antes, "despues", rol + " " + claves(carreras));
        return u;
    }

    /** HU 6.1/6.3: dar de baja o reactivar una cuenta sin borrar su historial. */
    public Usuario definirActiva(UUID idActor, UUID idCuenta, boolean activa) {
        Usuario actor = guardia.exigir(idActor, Permiso.GESTIONAR_CARRERA);
        Usuario u = usuarios.buscar(idCuenta).orElseThrow(() -> new NoEncontradoException("La cuenta no existe."));
        if (u.equals(actor)) {
            throw new ReglaNegocioException("No puedes desactivar tu propia cuenta.");
        }
        exigirACargo(actor, u);
        u.setActivo(activa);
        auditoria.registrar(actor, Accion.CAMBIO_ESTADO_CUENTA, ENTIDAD, u.getId(), "correo", u.getCorreo(),
                "activa", activa);
        return u;
    }

    /**
     * Coordinación solo maneja cuentas de su carrera: alumnos de sus carreras y coordinaciones cuyas
     * carreras son todas suyas. Una cuenta de jefatura o de otra carrera no es suya.
     */
    private void exigirACargo(Usuario actor, Usuario u) {
        if (actor.esJefe()) {
            return;
        }
        boolean suya = u.esEstudiante() ? actor.atiendeCarrera(u.getCarrera())
                : u.esCoordinador() && actor.getCarrerasAsignadas().containsAll(u.getCarrerasAsignadas());
        if (!suya) {
            throw new AccesoDenegadoException("Esa cuenta no es de tu carrera: la gestiona jefatura.");
        }
    }

    // ---------------------------------------------------------------------

    private Usuario personalExistente(UUID id) {
        Usuario u = usuarios.buscar(id).orElseThrow(() -> new NoEncontradoException("La cuenta no existe."));
        if (u.esEstudiante()) {
            throw new ReglaNegocioException("Las cuentas de estudiante no tienen rol de personal.");
        }
        return u;
    }

    /**
     * Solo coordinación lleva carreras, y al menos una (sin carreras no vería ninguna bandeja).
     * Coordinación solo otorga el rol de coordinación y solo con sus propias carreras.
     */
    private Set<Carrera> carrerasPara(Usuario actor, Rol rol, Set<UUID> ids) {
        if (Reglas.requerido(rol, "Elige el rol.") == Rol.ESTUDIANTE) {
            throw new ReglaNegocioException("El rol de estudiante no se asigna: llega con el primer acceso.");
        }
        if (!actor.esJefe() && rol != Rol.COORDINADOR) {
            throw new AccesoDenegadoException("Coordinación solo puede asignar el rol de coordinación.");
        }
        Set<Carrera> carreras = catalogo.carrerasPorId(ids);
        if (rol == Rol.COORDINADOR && carreras.isEmpty()) {
            throw new ReglaNegocioException("Asigna al menos una carrera a la coordinación.");
        }
        if (rol != Rol.COORDINADOR) {
            return new HashSet<>();
        }
        guardia.exigirCarreras(actor, carreras);
        return carreras;
    }

    private static String claves(Set<Carrera> carreras) {
        StringBuilder sb = new StringBuilder();
        for (Carrera c : carreras) {
            sb.append(sb.length() == 0 ? "" : ",").append(c.getClave());
        }
        return sb.toString();
    }
}
