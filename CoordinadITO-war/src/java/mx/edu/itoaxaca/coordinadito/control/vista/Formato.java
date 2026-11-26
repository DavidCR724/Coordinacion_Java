package mx.edu.itoaxaca.coordinadito.control.vista;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Fechas;

/**
 * Cómo se muestran fechas y estados en las vistas.
 * Las vistas heredaron del front en React unos códigos de estado en inglés
 * (received, in-review...); aquí se traducen desde el ENUM de la base para no reescribir los componentes.
 */
public final class Formato {

    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun",
                                           "jul", "ago", "sep", "oct", "nov", "dic"};

    private Formato() { }

    /** 27 oct 2026 */
    public static String fecha(LocalDate d) {
        return d == null ? "" : d.getDayOfMonth() + " " + MESES[d.getMonthValue() - 1] + " " + d.getYear();
    }

    /** 27 oct 2026 (en hora de Oaxaca) */
    public static String fecha(OffsetDateTime t) {
        return t == null ? "" : fecha(local(t).toLocalDate());
    }

    /** 27 oct 2026, 10:05 a.m. */
    public static String fechaHora(OffsetDateTime t) {
        if (t == null) {
            return "";
        }
        ZonedDateTime z = local(t);
        int h = z.getHour() % 12 == 0 ? 12 : z.getHour() % 12;
        return fecha(z.toLocalDate()) + ", " + h + ":" + String.format("%02d", z.getMinute())
                + (z.getHour() < 12 ? " a.m." : " p.m.");
    }

    /** Valor ordenable para las columnas de fecha (2026-10-27T10:05). */
    public static String iso(OffsetDateTime t) {
        return t == null ? "" : local(t).toLocalDateTime().toString();
    }

    public static String iso(LocalDate d) {
        return d == null ? "" : d.toString();
    }

    /** Código que usan comp-insignia-estado y comp-evento-historial. */
    public static String codigo(EstadoSolicitud e) {
        if (e == null) {
            return "received";
        }
        switch (e) {
            case PROCESANDO: return "in-review";
            case ESPERA: return "waiting";
            case FINALIZADA: return "completed";
            case RECHAZADA: return "rejected";
            default: return "received";
        }
    }

    public static EstadoSolicitud estado(String codigo) {
        if (codigo == null) {
            return null;
        }
        switch (codigo) {
            case "received": return EstadoSolicitud.ENVIADA;
            case "in-review": return EstadoSolicitud.PROCESANDO;
            case "waiting": return EstadoSolicitud.ESPERA;
            case "completed": return EstadoSolicitud.FINALIZADA;
            case "rejected": return EstadoSolicitud.RECHAZADA;
            default: return null;
        }
    }

    public static String iniciales(String nombres, String apellido) {
        StringBuilder sb = new StringBuilder();
        if (nombres != null && !nombres.trim().isEmpty()) {
            sb.append(Character.toUpperCase(nombres.trim().charAt(0)));
        }
        if (apellido != null && !apellido.trim().isEmpty()) {
            sb.append(Character.toUpperCase(apellido.trim().charAt(0)));
        }
        return sb.toString();
    }

    public static String tamano(Long bytes) {
        if (bytes == null) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static ZonedDateTime local(OffsetDateTime t) {
        return t.atZoneSameInstant(Fechas.ZONA);
    }
}
