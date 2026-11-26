package modelo.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Zona horaria única del sistema (el ITO está en Oaxaca). Evita que cada PC use la suya. */
public final class Fechas {

    public static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private Fechas() { }

    public static OffsetDateTime ahora() {
        return OffsetDateTime.now(ZONA);
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    /** Primer instante del día en la zona del ITO (null si no hay día). */
    public static OffsetDateTime inicio(LocalDate dia) {
        return dia == null ? null : dia.atStartOfDay(ZONA).toOffsetDateTime();
    }

    /** Primer instante del día SIGUIENTE: límite superior exclusivo para «hasta este día». */
    public static OffsetDateTime finExclusivo(LocalDate dia) {
        return dia == null ? null : inicio(dia.plusDays(1));
    }

    /** Días completos transcurridos desde un instante hasta ahora. */
    public static long diasDesde(OffsetDateTime instante) {
        return java.time.Duration.between(instante, ahora()).toDays();
    }
}
