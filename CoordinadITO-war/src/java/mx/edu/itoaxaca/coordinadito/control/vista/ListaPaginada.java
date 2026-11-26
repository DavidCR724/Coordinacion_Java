package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import modelo.bean.Pagina;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;

/**
 * Adaptador entre una consulta paginada del EJB ({@link Pagina}) y un p:dataTable lazy.
 * La tabla pide solo la página que muestra; cada pantalla aporta la consulta y cómo convertir
 * cada entidad en su fila (patrón Adapter). Los filtros los lee la consulta de los campos del control.
 *
 * @param <E> entidad que devuelve el EJB
 * @param <R> fila que lee la vista
 */
public class ListaPaginada<E, R> extends LazyDataModel<R> {

    private static final long serialVersionUID = 1L;

    /** Una consulta paginada del EJB: número de página (desde 0) y tamaño. */
    @FunctionalInterface
    public interface Consulta<E> extends Serializable {
        Pagina<E> pagina(int numero, int tamano);
    }

    /** Conversión serializable (el control @ViewScoped viaja en la sesión). */
    @FunctionalInterface
    public interface Conversion<E, R> extends Function<E, R>, Serializable { }

    private final Consulta<E> consulta;
    private final Conversion<E, R> conversion;
    private final Conversion<R, String> clave;

    public ListaPaginada(Consulta<E> consulta, Conversion<E, R> conversion, Conversion<R, String> clave) {
        this.consulta = consulta;
        this.conversion = conversion;
        this.clave = clave;
    }

    @Override
    public int count(Map<String, FilterMeta> filtros) {
        Pagina<E> p = Mensajes.intentar(() -> consulta.pagina(0, 1));
        return p == null ? 0 : (int) p.getTotal();
    }

    @Override
    public List<R> load(int primero, int tamano, Map<String, SortMeta> orden, Map<String, FilterMeta> filtros) {
        int t = Math.max(1, tamano);
        Pagina<E> p = Mensajes.intentar(() -> consulta.pagina(primero / t, t));
        if (p == null) {
            return Collections.emptyList();
        }
        setRowCount((int) p.getTotal());
        return p.getElementos().stream().map(conversion).collect(Collectors.toList());
    }

    @Override
    public String getRowKey(R fila) {
        return clave.apply(fila);
    }
}
