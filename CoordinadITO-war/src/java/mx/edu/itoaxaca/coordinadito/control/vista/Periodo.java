package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import modelo.entity.Fechas;

/**
 * Periodo escolar para los paneles de seguimiento (HU 7.1 – 7.4): «2026-1» es enero–junio y
 * «2026-2», agosto–diciembre. Se identifica con un texto para que viaje en un p:selectOneMenu.
 */
public final class Periodo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int anio;
    private final int mitad;

    private Periodo(int anio, int mitad) {
        this.anio = anio;
        this.mitad = mitad;
    }

    /** El periodo en curso (julio cuenta como el de enero–junio). */
    public static Periodo actual() {
        LocalDate hoy = Fechas.hoy();
        return new Periodo(hoy.getYear(), hoy.getMonthValue() >= 8 ? 2 : 1);
    }

    /** El actual y los n − 1 anteriores, del más reciente al más antiguo. */
    public static List<Periodo> recientes(int n) {
        List<Periodo> r = new ArrayList<>();
        Periodo p = actual();
        for (int i = 0; i < n; i++) {
            r.add(p);
            p = p.anterior();
        }
        return r;
    }

    /** «2026-2» → periodo; cualquier otra cosa → el actual. */
    public static Periodo de(String id) {
        try {
            String[] partes = id.split("-");
            int mitad = Integer.parseInt(partes[1]);
            return mitad == 1 || mitad == 2 ? new Periodo(Integer.parseInt(partes[0]), mitad) : actual();
        } catch (RuntimeException e) {
            return actual();
        }
    }

    private Periodo anterior() {
        return mitad == 2 ? new Periodo(anio, 1) : new Periodo(anio - 1, 2);
    }

    public String getId() { return anio + "-" + mitad; }
    public String getEtiqueta() { return (mitad == 1 ? "Ene–Jun " : "Ago–Dic ") + anio; }
    /** Primer día (inclusive). Enero–junio incluye julio para no dejar fuera el verano. */
    public LocalDate getDesde() { return mitad == 1 ? LocalDate.of(anio, 1, 1) : LocalDate.of(anio, 8, 1); }
    /** Último día (inclusive). */
    public LocalDate getHasta() { return mitad == 1 ? LocalDate.of(anio, 7, 31) : LocalDate.of(anio, 12, 31); }
}
