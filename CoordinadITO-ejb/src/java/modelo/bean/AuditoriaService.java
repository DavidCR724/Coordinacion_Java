package modelo.bean;

import acceso.datos.AuditoriaRepository;
import acceso.datos.UsuarioRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import modelo.entity.Accion;
import modelo.entity.Carrera;
import modelo.entity.Fechas;
import modelo.entity.RegistroAuditoria;
import modelo.entity.Usuario;

/**
 * «servicio» Bitácora de auditoría (HU 8.2): un solo punto por donde pasan todos los registros.
 *
 * registrar() corre dentro de la transacción de quien hace el cambio: si el cambio se deshace,
 * el registro también (no queda constancia de algo que no pasó). registrarAparte() abre su
 * propia transacción para los intentos fallidos, que deben quedar aunque la operación falle.
 */
@Stateless
public class AuditoriaService {

    @EJB private AuditoriaRepository registros;
    @EJB private UsuarioRepository usuarios;
    @EJB private Guardia guardia;

    public void registrar(Usuario actor, Accion accion, String entidad, Object idEntidad, Object... detalle) {
        registros.guardar(new RegistroAuditoria(actor, accion, entidad,
                idEntidad == null ? null : idEntidad.toString(), json(detalle), ContextoPeticion.ip()));
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void registrarAparte(UUID idActor, Accion accion, String entidad, Object idEntidad, Object... detalle) {
        registrar(idActor == null ? null : usuarios.buscar(idActor).orElse(null), accion, entidad, idEntidad, detalle);
    }

    /**
     * HU 8.2: jefatura consulta toda la bitácora; una coordinación, lo que hicieron las personas de
     * sus carreras (alumnos y coordinaciones). Nunca se edita ni se borra.
     */
    public Pagina<RegistroAuditoria> consultar(UUID idActor, Accion accion, LocalDate desde, LocalDate hasta,
                                               String texto, int pagina, int tamano) {
        Usuario actor = guardia.exigir(idActor, Permiso.GESTIONAR_CARRERA);
        Set<Carrera> alcance = guardia.alcance(actor);
        Reglas.periodo(desde, hasta);
        int p = Math.max(0, pagina);
        return new Pagina<>(registros.buscar(accion, Fechas.inicio(desde), Fechas.finExclusivo(hasta), texto, alcance, p * tamano, tamano),
                registros.contar(accion, Fechas.inicio(desde), Fechas.finExclusivo(hasta), texto, alcance), p, tamano);
    }

    /** Pares clave, valor → objeto JSON (columna JSONB). Los null se omiten. */
    static String json(Object... pares) {
        if (pares == null || pares.length == 0) {
            return null;
        }
        JsonObjectBuilder b = Json.createObjectBuilder();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            if (pares[i + 1] != null) {
                b.add(String.valueOf(pares[i]), String.valueOf(pares[i + 1]));
            }
        }
        return b.build().toString();
    }
}
