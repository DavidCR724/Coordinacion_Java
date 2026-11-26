package mx.edu.itoaxaca.coordinadito.control.vista;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Builder de la configuración de Chart.js que dibuja comp-grafica-barras (p:chart).
 * <pre>
 *   GraficaBarras.de(filas, Fila::getNombre)
 *       .serie("Recibidas", Fila::getRecibidas, AZUL)
 *       .serie("Finalizadas", Fila::getFinalizadas, VERDE)
 *       .horizontal().apilada().json();
 * </pre>
 * El JSON lo arma jakarta.json, así no hay que escapar comillas a mano en EL.
 */
public final class GraficaBarras<T> {

    public static final String AZUL = "#2b6cb0";
    public static final String NARANJA = "#ff8c1a";
    public static final String AMARILLO = "#d69e2e";
    public static final String VERDE = "#2e7d32";
    public static final String ROJO = "#b23b3b";
    public static final String MARINO = "#12233f";

    private final Collection<T> filas;
    private final JsonArrayBuilder etiquetas = Json.createArrayBuilder();
    private final JsonArrayBuilder series = Json.createArrayBuilder();
    private int numeroSeries;
    private boolean horizontal;
    private boolean apilada;

    private GraficaBarras(Collection<T> filas, Function<T, String> etiqueta) {
        this.filas = filas;
        for (T f : filas) {
            etiquetas.add(etiqueta.apply(f));
        }
    }

    public static <T> GraficaBarras<T> de(Collection<T> filas, Function<T, String> etiqueta) {
        return new GraficaBarras<>(filas, etiqueta);
    }

    public GraficaBarras<T> serie(String nombre, ToLongFunction<T> valor, String color) {
        JsonArrayBuilder datos = Json.createArrayBuilder();
        for (T f : filas) {
            datos.add(valor.applyAsLong(f));
        }
        series.add(Json.createObjectBuilder().add("label", nombre).add("data", datos)
                .add("backgroundColor", color).add("borderRadius", 4).add("maxBarThickness", 36));
        numeroSeries++;
        return this;
    }

    public GraficaBarras<T> horizontal() {
        horizontal = true;
        return this;
    }

    public GraficaBarras<T> apilada() {
        apilada = true;
        return this;
    }

    public String json() {
        JsonObjectBuilder opciones = Json.createObjectBuilder()
                .add("indexAxis", horizontal ? "y" : "x")
                .add("responsive", true)
                .add("maintainAspectRatio", false)
                .add("plugins", Json.createObjectBuilder().add("legend",
                        Json.createObjectBuilder().add("display", numeroSeries > 1).add("position", "bottom")))
                .add("scales", Json.createObjectBuilder()
                        .add("x", eje(horizontal))
                        .add("y", eje(!horizontal).add("beginAtZero", true)));
        return Json.createObjectBuilder()
                .add("type", "bar")
                .add("data", Json.createObjectBuilder().add("labels", etiquetas).add("datasets", series))
                .add("options", opciones)
                .build().toString();
    }

    private JsonObjectBuilder eje(boolean conRejilla) {
        return Json.createObjectBuilder().add("stacked", apilada)
                .add("grid", Json.createObjectBuilder().add("display", conRejilla));
    }
}
