package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** Una opción de un p:selectOneMenu / p:selectCheckboxMenu: valor que viaja y texto que se ve. */
public final class Opcion implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String valor;
    private final String etiqueta;

    public Opcion(String valor, String etiqueta) {
        this.valor = valor;
        this.etiqueta = etiqueta;
    }

    /** Convierte cualquier lista en opciones (una línea en cada control). */
    public static <T> List<Opcion> de(Collection<T> origen, Function<T, Object> valor, Function<T, String> etiqueta) {
        List<Opcion> r = new ArrayList<>();
        for (T t : origen) {
            r.add(new Opcion(String.valueOf(valor.apply(t)), etiqueta.apply(t)));
        }
        return r;
    }

    public String getValor() { return valor; }
    public String getEtiqueta() { return etiqueta; }
}
