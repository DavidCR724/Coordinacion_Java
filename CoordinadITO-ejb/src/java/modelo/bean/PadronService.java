package modelo.bean;

import acceso.datos.CarreraRepository;
import acceso.datos.PadronRepository;
import acceso.datos.UsuarioRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import modelo.entity.Accion;
import modelo.entity.CargaPadron;
import modelo.entity.Carrera;
import modelo.entity.OrigenDatos;
import modelo.entity.PadronAlumno;
import modelo.entity.Usuario;

/**
 * «servicio» Carga del padrón institucional (HU 8.1).
 *
 * Primero se valida TODO el archivo; si el formato es incorrecto (faltan columnas, no hay ninguna
 * fila válida) se rechaza sin tocar la base. Después, en una sola transacción, se crean o actualizan
 * los registros del padrón, se sincronizan las cuentas de los alumnos que ya entraron (sus datos
 * pasan a ser «del padrón») y quien no viene en el archivo deja de estar vigente.
 *
 * Formato (CSV con encabezado, separado por comas o punto y coma):
 * num_control, correo, nombres, apellido_paterno, apellido_materno, carrera (clave), semestre
 */
@Stateless
public class PadronService {

    static final String[] COLUMNAS = {"num_control", "correo", "nombres", "apellido_paterno", "apellido_materno", "carrera", "semestre"};
    private static final int MAX_ERRORES = 50;
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    @EJB private PadronRepository padron;
    @EJB private CarreraRepository carreras;
    @EJB private UsuarioRepository usuarios;
    @EJB private Guardia guardia;
    @EJB private AuditoriaService auditoria;

    /**
     * HU 8.1. Jefatura carga el padrón de cualquier carrera; una coordinación, solo el de las suyas:
     * las filas de otra carrera se rechazan y los ausentes solo se retiran dentro de sus carreras.
     */
    public ResultadoCarga cargar(UUID idActor, String nombreArchivo, byte[] contenido) {
        Usuario actor = guardia.exigir(idActor, Permiso.GESTIONAR_CARRERA);
        Set<Carrera> alcance = guardia.alcance(actor);
        if (contenido == null || contenido.length == 0) {
            throw new ReglaNegocioException("El archivo está vacío.");
        }
        if (contenido.length > MAX_BYTES) {
            throw new ReglaNegocioException("El archivo pesa más de 10 MB.");
        }
        LectorCsv csv = LectorCsv.leer(contenido);
        List<String> faltan = csv.faltantes(COLUMNAS);
        if (!faltan.isEmpty()) {
            throw new ReglaNegocioException("Formato incorrecto: faltan las columnas " + String.join(", ", faltan)
                    + ". No se modificó nada.");
        }
        List<Map<String, String>> registros = csv.registros();
        Map<String, Carrera> porClave = new HashMap<>();
        for (Carrera c : carreras.ordenadas()) {
            porClave.put(c.getClave().toUpperCase(), c);
        }

        // 1. Validar todas las filas sin escribir nada.
        List<String> errores = new ArrayList<>();
        Map<String, FilaPadron> validas = new LinkedHashMap<>();
        for (int i = 0; i < registros.size(); i++) {
            try {
                FilaPadron f = FilaPadron.de(registros.get(i), porClave);
                if (alcance != null && !alcance.contains(f.carrera)) {
                    throw new ReglaNegocioException("la carrera " + f.carrera.getClave() + " no es de tu coordinación.");
                }
                if (validas.containsKey(f.numControl)) {
                    throw new ReglaNegocioException("el número de control " + f.numControl + " está repetido en el archivo.");
                }
                Optional<PadronAlumno> conCorreo = padron.unoPorCorreo(f.correo);
                if (conCorreo.isPresent() && !conCorreo.get().getNumControl().equals(f.numControl)) {
                    throw new ReglaNegocioException("el correo " + f.correo + " ya pertenece al número de control "
                            + conCorreo.get().getNumControl() + ".");
                }
                if (alcance != null) {
                    // Un alumno que hoy es de otra carrera no se lo puede llevar una coordinación: lo cambia jefatura.
                    Optional<PadronAlumno> actual = padron.buscar(f.numControl);
                    if (actual.isPresent() && !alcance.contains(actual.get().getCarrera())) {
                        throw new ReglaNegocioException("el número de control " + f.numControl
                                + " está en el padrón de otra carrera; ese cambio lo hace jefatura.");
                    }
                }
                validas.put(f.numControl, f);
            } catch (ReglaNegocioException e) {
                if (errores.size() < MAX_ERRORES) {
                    errores.add("Fila " + (i + 2) + ": " + e.getMessage());
                }
            }
        }
        if (validas.isEmpty()) {
            throw new ReglaNegocioException("Ninguna fila es válida; no se modificó nada."
                    + (errores.isEmpty() ? "" : " Primer error: " + errores.get(0)));
        }

        // 2. Aplicar.
        CargaPadron carga = new CargaPadron(actor, Reglas.texto(nombreArchivo, "El nombre del archivo", 255), registros.size());
        padron.guardarCarga(carga);
        for (int i = validas.size(); i < registros.size(); i++) {
            carga.contarRechazado();
        }
        for (FilaPadron f : validas.values()) {
            Optional<PadronAlumno> existente = padron.buscar(f.numControl);
            PadronAlumno p = existente.orElseGet(() -> new PadronAlumno(f.numControl));
            boolean cambio = p.actualizar(f.correo, f.nombres, f.apellidoPaterno, f.apellidoMaterno, f.carrera, f.semestre, carga);
            if (!existente.isPresent()) {
                padron.guardar(p);
                carga.contarNuevo();
            } else if (cambio) {
                carga.contarActualizado();
            }
            sincronizarCuenta(f);
        }
        int retirados = padron.retirarAusentes(validas.keySet(), alcance);
        auditoria.registrar(actor, Accion.CARGAR_PADRON, "CARGA_PADRON", carga.getId(), "archivo", carga.getNombreArchivo(),
                "nuevos", carga.getNuevos(), "actualizados", carga.getActualizados(), "rechazados", carga.getRechazados(),
                "retirados", retirados);
        return new ResultadoCarga(carga, errores, retirados);
    }

    /** Jefatura ve todas las cargas; una coordinación, las que subió ella. */
    public List<CargaPadron> cargas(UUID idActor) {
        Usuario actor = guardia.exigir(idActor, Permiso.GESTIONAR_CARRERA);
        return padron.cargas(actor.esJefe() ? null : actor, 20);
    }

    /** Archivo de ejemplo con las columnas que espera {@link #cargar} (un solo lugar las define). */
    public byte[] plantilla() {
        String[] ejemplo = {"00000001", "00000001@" + AccesoService.DOMINIO, "Nombre", "Apellido", "Materno", "ISC", "5"};
        Csv<String[]> csv = new Csv<>();
        for (int i = 0; i < COLUMNAS.length; i++) {
            final int columna = i;
            csv.columna(COLUMNAS[i], fila -> fila[columna]);
        }
        return csv.escribir(java.util.Collections.singletonList(ejemplo));
    }

    /** Alumnos vigentes en el padrón de las carreras del actor. */
    public long vigentes(UUID idActor) {
        Usuario actor = guardia.exigir(idActor, Permiso.GESTIONAR_CARRERA);
        return padron.vigentes(guardia.alcance(actor));
    }

    /** Los datos del padrón sustituyen a los autodeclarados en la cuenta del alumno (si ya entró). */
    private void sincronizarCuenta(FilaPadron f) {
        Optional<Usuario> cuenta = usuarios.porNumControl(f.numControl);
        if (!cuenta.isPresent()) {
            cuenta = usuarios.porCorreo(f.correo);
        }
        cuenta.filter(Usuario::esEstudiante).ifPresent(u -> {
            u.nombrar(f.nombres, f.apellidoPaterno, f.apellidoMaterno);
            u.actualizarDatosEscolares(f.carrera, f.semestre, OrigenDatos.PADRON);
        });
    }

    /** Una fila ya validada. */
    private static final class FilaPadron {
        String numControl;
        String correo;
        String nombres;
        String apellidoPaterno;
        String apellidoMaterno;
        Carrera carrera;
        Short semestre;

        static FilaPadron de(Map<String, String> r, Map<String, Carrera> carreras) {
            FilaPadron f = new FilaPadron();
            f.numControl = Reglas.texto(r.get("num_control"), "El número de control", 10).toUpperCase();
            if (!f.numControl.matches("[A-Z]?\\d{8,9}")) {
                throw new ReglaNegocioException("el número de control «" + f.numControl + "» no es válido.");
            }
            f.correo = Reglas.texto(r.get("correo"), "El correo", 150).toLowerCase();
            if (!f.correo.endsWith("@" + AccesoService.DOMINIO)) {
                throw new ReglaNegocioException("el correo " + f.correo + " no es institucional.");
            }
            f.nombres = Reglas.texto(r.get("nombres"), "El nombre", 100);
            f.apellidoPaterno = Reglas.texto(r.get("apellido_paterno"), "El apellido paterno", 100);
            f.apellidoMaterno = Reglas.opcional(r.get("apellido_materno"), "El apellido materno", 100);
            String clave = Reglas.texto(r.get("carrera"), "La carrera", 20).toUpperCase();
            f.carrera = carreras.get(clave);
            if (f.carrera == null) {
                throw new ReglaNegocioException("la carrera " + clave + " no existe.");
            }
            String sem = Reglas.limpio(r.get("semestre"));
            try {
                f.semestre = sem == null ? null : Reglas.semestre(Integer.valueOf(sem));
            } catch (NumberFormatException e) {
                throw new ReglaNegocioException("el semestre «" + sem + "» no es un número.");
            }
            return f;
        }
    }
}
