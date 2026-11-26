package modelo.bean;

import acceso.datos.SolicitudRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import modelo.entity.Accion;
import modelo.entity.Carrera;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Fechas;
import modelo.entity.Solicitud;
import modelo.entity.Usuario;

/**
 * «servicio» Panel de seguimiento de jefatura (HU 7.1 a 7.4).
 * Todo sale de las solicitudes registradas en el sistema: no hay contadores aparte que se
 * puedan desincronizar.
 */
@Stateless
public class SeguimientoService {

    @EJB private SolicitudRepository solicitudes;
    @EJB private CatalogoService catalogo;
    @EJB private Guardia guardia;
    @EJB private AuditoriaService auditoria;

    /** HU 7.1: volumen por tipo de trámite y por carrera, desglosado por estado (mayor total primero). */
    public Seguimiento.Volumen volumen(UUID idJefe, LocalDate desde, LocalDate hasta) {
        List<Solicitud> lista = registradas(idJefe, desde, hasta);
        return new Seguimiento.Volumen(filas(lista, s -> s.getTramite().getNombre()),
                filas(lista, s -> s.getCarrera().getClave()),   // clave: cabe en la gráfica (ISC, IC...)
                new Seguimiento.Fila("Total", porEstado(lista)));
    }

    /** HU 7.2: tiempo de resolución por tipo; solo finalizadas o rechazadas. */
    public List<Seguimiento.Tiempo> tiempos(UUID idJefe, LocalDate desde, LocalDate hasta) {
        Map<String, List<Solicitud>> porTramite = registradas(idJefe, desde, hasta).stream()
                .filter(s -> s.getFechaCierre() != null)
                .collect(Collectors.groupingBy(s -> s.getTramite().getNombre(), LinkedHashMap::new, Collectors.toList()));
        List<Seguimiento.Tiempo> r = new ArrayList<>();
        for (Map.Entry<String, List<Solicitud>> e : porTramite.entrySet()) {
            List<Double> dias = e.getValue().stream().map(SeguimientoService::diasDeResolucion).collect(Collectors.toList());
            int esperados = e.getValue().get(0).getTramite().getDiasEsperados();
            r.add(new Seguimiento.Tiempo(e.getKey(), dias.size(),
                    dias.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                    dias.stream().mapToDouble(Double::doubleValue).min().orElse(0),
                    dias.stream().mapToDouble(Double::doubleValue).max().orElse(0),
                    esperados, (int) dias.stream().filter(d -> d <= esperados).count()));
        }
        r.sort(Comparator.comparingDouble(Seguimiento.Tiempo::getPromedioDias).reversed());
        return r;
    }

    /** HU 7.3: abiertas que rebasan los días esperados de su trámite, de la más atrasada a la menos. */
    public List<Seguimiento.Rezago> rezagos(UUID idJefe, UUID idCarrera) {
        guardia.exigir(idJefe, Permiso.SEGUIMIENTO);
        java.util.Set<Carrera> carreras = idCarrera == null ? null : catalogo.carrerasPorId(java.util.Collections.singleton(idCarrera));
        List<Seguimiento.Rezago> r = new ArrayList<>();
        for (Solicitud s : solicitudes.abiertas(carreras)) {
            long dias = Fechas.diasDesde(s.getFechaRegistro());
            if (dias > s.getTramite().getDiasEsperados()) {
                r.add(new Seguimiento.Rezago(s, dias));
            }
        }
        r.sort(Comparator.comparingLong(Seguimiento.Rezago::getExceso).reversed());
        return r;
    }

    /** HU 7.4: historial del periodo en CSV (se abre en Excel). Queda en la auditoría. */
    public byte[] exportar(UUID idJefe, LocalDate desde, LocalDate hasta) {
        Usuario jefe = guardia.exigir(idJefe, Permiso.SEGUIMIENTO);
        List<Solicitud> lista = registradas(idJefe, desde, hasta);
        auditoria.registrar(jefe, Accion.EXPORTAR_HISTORIAL, "SOLICITUD", null,
                "desde", desde, "hasta", hasta, "filas", lista.size());
        return new Csv<Solicitud>()
                .columna("Folio", Solicitud::getFolioTexto)
                .columna("Trámite", s -> s.getTramite().getNombre())
                .columna("Carrera", s -> s.getCarrera().getNombre())
                .columna("Número de control", s -> s.getSolicitante().getNumControl())
                .columna("Prioridad", s -> s.getPrioridad().getEtiqueta())
                .columna("Fecha de registro", s -> s.getFechaRegistro().atZoneSameInstant(Fechas.ZONA).toLocalDateTime())
                .columna("Fecha de resolución", s -> s.getFechaCierre() == null ? null : s.getFechaCierre().atZoneSameInstant(Fechas.ZONA).toLocalDateTime())
                .columna("Estado final", s -> s.getEstado().getEtiqueta())
                .escribir(lista);
    }

    // ---------------------------------------------------------------------

    private List<Solicitud> registradas(UUID idJefe, LocalDate desde, LocalDate hasta) {
        guardia.exigir(idJefe, Permiso.SEGUIMIENTO);
        Reglas.requerido(desde, "Indica desde qué fecha.");
        Reglas.requerido(hasta, "Indica hasta qué fecha.");
        Reglas.periodo(desde, hasta);
        return solicitudes.registradasEntre(Fechas.inicio(desde), Fechas.finExclusivo(hasta));
    }

    /** Una fila por grupo (trámite o carrera) con su conteo por estado, de mayor a menor total. */
    private static List<Seguimiento.Fila> filas(List<Solicitud> lista, Function<Solicitud, String> grupo) {
        return lista.stream().collect(Collectors.groupingBy(grupo, Collectors.toList())).entrySet().stream()
                .map(e -> new Seguimiento.Fila(e.getKey(), porEstado(e.getValue())))
                .sorted(Comparator.comparingLong(Seguimiento.Fila::getTotal).reversed().thenComparing(Seguimiento.Fila::getNombre))
                .collect(Collectors.toList());
    }

    private static Map<EstadoSolicitud, Long> porEstado(List<Solicitud> lista) {
        return lista.stream().collect(Collectors.groupingBy(Solicitud::getEstado,
                () -> new EnumMap<>(EstadoSolicitud.class), Collectors.counting()));
    }

    private static double diasDeResolucion(Solicitud s) {
        return Duration.between(s.getFechaRegistro(), s.getFechaCierre()).toMinutes() / (60.0 * 24);
    }
}
