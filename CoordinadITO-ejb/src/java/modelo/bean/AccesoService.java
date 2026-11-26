package modelo.bean;

import acceso.datos.CarreraRepository;
import acceso.datos.PadronRepository;
import acceso.datos.UsuarioRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import modelo.entity.Accion;
import modelo.entity.Carrera;
import modelo.entity.Fechas;
import modelo.entity.OrigenDatos;
import modelo.entity.PadronAlumno;
import modelo.entity.Usuario;

/**
 * «servicio» Acceso al sistema (HU 6.1, 6.2, 6.4 y la parte de accesos de 8.2).
 *
 * Recibe una {@link Identidad} ya verificada por el proveedor institucional y decide:
 * <ol>
 *   <li>Correo fuera de @itoaxaca.edu.mx → rechazado.</li>
 *   <li>Ya tiene cuenta → entra (si está activa).</li>
 *   <li>No tiene cuenta y el correo es un número de control → estudiante: si está en el padrón,
 *       la cuenta se crea con esos datos; si no, pasa al primer acceso.</li>
 *   <li>Cualquier otro correo sin cuenta → rechazado: el rol de personal lo asigna su coordinación o jefatura
 *       (HU 6.3), nunca se deduce del correo.</li>
 * </ol>
 * Todos los intentos (buenos y fallidos) quedan en la auditoría.
 */
@Stateless
public class AccesoService {

    /** Dominio institucional. Se puede cambiar con la propiedad de sistema coordinadito.dominio. */
    static final String DOMINIO = System.getProperty("coordinadito.dominio", "itoaxaca.edu.mx");

    /** El usuario del correo de un estudiante es su número de control (p. ej. 22161023, C22161023). */
    private static final Pattern NUM_CONTROL = Pattern.compile("[a-z]?\\d{8,9}");

    @EJB private UsuarioRepository usuarios;
    @EJB private PadronRepository padron;
    @EJB private CarreraRepository carreras;
    @EJB private AuditoriaService auditoria;
    @EJB private CodigoAccesoService codigos;

    public ResultadoAcceso ingresar(Identidad id) {
        if (id.getCorreo() == null || !DOMINIO.equals(id.getDominio())) {
            throw rechazo(id, null, "Entra con tu cuenta institucional @" + DOMINIO + ".");
        }
        Optional<Usuario> cuenta = usuarios.porCorreo(id.getCorreo());
        if (cuenta.isPresent()) {
            return admitir(cuenta.get(), id);
        }
        if (!esNumeroControl(id)) {
            throw rechazo(id, null, "Tu cuenta no tiene un rol asignado en CoordinadITO. Pide a tu coordinación o a jefatura que te dé acceso.");
        }
        Optional<PadronAlumno> inscrito = padron.vigentePorCorreo(id.getCorreo());
        if (!inscrito.isPresent()) {
            return ResultadoAcceso.primerAcceso(id);
        }
        PadronAlumno p = inscrito.get();
        Usuario nuevo = Usuario.estudiante(id.getCorreo(), p.getNombres(), p.getApellidoPaterno(), p.getApellidoMaterno(),
                p.getNumControl(), p.getCarrera(), p.getSemestre(), OrigenDatos.PADRON);
        usuarios.guardar(nuevo);
        auditoria.registrar(nuevo, Accion.PRIMER_ACCESO, "USUARIO", nuevo.getId(), "origen", "PADRON");
        return admitir(nuevo, id);
    }

    /**
     * Acceso con código por correo (CodigoAccesoService): si el código es el correcto, la persona
     * demostró que el buzón es suyo y entra por el mismo camino que con la cuenta institucional.
     * El nombre no viene en el correo: si es su primer acceso, lo escribe en primer-acceso.
     */
    public ResultadoAcceso ingresarConCodigo(String correo, String codigo) {
        Identidad id = new Identidad(CodigoAccesoService.normalizar(correo), null, null);
        switch (codigos.comprobar(id.getCorreo(), codigo)) {
            case CORRECTO:
                return ingresar(id);
            case INCORRECTO:
                throw rechazo(id, null, "El código no coincide. Revísalo e intenta de nuevo.");
            case AGOTADO:
                throw rechazo(id, null, "Demasiados intentos con ese código. Pide uno nuevo.");
            case VENCIDO:
                throw rechazo(id, null, "El código ya venció. Pide uno nuevo.");
            default:
                throw rechazo(id, null, "No hay un código vigente para ese correo. Pide uno nuevo.");
        }
    }

    /**
     * HU 6.4: el estudiante que no está en el padrón declara carrera y semestre. El número de
     * control sale del correo, nunca de lo que capture (HU 6.2).
     */
    public Usuario completarPrimerAcceso(Identidad id, UUID idCarrera, Integer semestre) {
        if (!DOMINIO.equals(id.getDominio()) || !esNumeroControl(id)) {
            throw new AccesoDenegadoException("Esta cuenta no corresponde a un estudiante.");
        }
        if (usuarios.porCorreo(id.getCorreo()).isPresent()) {
            throw new ReglaNegocioException("Tu cuenta ya está registrada. Vuelve a iniciar sesión.");
        }
        Carrera carrera = carreras.buscar(Reglas.requerido(idCarrera, "Elige tu carrera."))
                .orElseThrow(() -> new NoEncontradoException("La carrera no existe."));
        String numControl = id.getUsuarioCorreo().toUpperCase();
        if (usuarios.porNumControl(numControl).isPresent()) {
            throw new ReglaNegocioException("El número de control " + numControl + " ya está registrado con otra cuenta. Acude a coordinación.");
        }
        Usuario u = Usuario.estudiante(id.getCorreo(),
                Reglas.texto(id.getNombres(), "Tu nombre", 100),
                Reglas.texto(id.getApellidoPaterno(), "Tu apellido", 100),
                id.getApellidoMaterno(), numControl, carrera, Reglas.semestre(semestre), OrigenDatos.AUTODECLARADO);
        u.setUltimoAcceso(Fechas.ahora());
        usuarios.guardar(u);
        auditoria.registrar(u, Accion.PRIMER_ACCESO, "USUARIO", u.getId(), "origen", "AUTODECLARADO", "carrera", carrera.getClave());
        return u;
    }

    /** Cuenta activa con datos frescos (para refrescar la sesión). */
    public Usuario cuenta(UUID id) {
        return usuarios.buscar(id).filter(Usuario::isActivo)
                .orElseThrow(() -> new AccesoDenegadoException("Sesión no válida."));
    }

    // ---------------------------------------------------------------------

    private ResultadoAcceso admitir(Usuario u, Identidad id) {
        if (!u.isActivo()) {
            throw rechazo(id, u.getId(), "Tu cuenta está desactivada. Comunícate con la coordinación.");
        }
        u.setUltimoAcceso(Fechas.ahora());
        auditoria.registrar(u, Accion.ACCESO, "USUARIO", u.getId());
        return ResultadoAcceso.admitido(u);
    }

    /** El intento fallido se guarda en su propia transacción: queda aunque esta se deshaga. */
    private AccesoDenegadoException rechazo(Identidad id, UUID idCuenta, String motivo) {
        auditoria.registrarAparte(idCuenta, Accion.ACCESO_FALLIDO, "USUARIO", id.getCorreo(), "motivo", motivo);
        return new AccesoDenegadoException(motivo);
    }

    static boolean esNumeroControl(Identidad id) {
        return NUM_CONTROL.matcher(id.getUsuarioCorreo()).matches();
    }
}
