package modelo.bean;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/** Un tramo de resultados más el total, para paginar tablas. */
public class Pagina<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<T> elementos;
    private final long total;
    private final int numero;
    private final int tamano;

    public Pagina(List<T> elementos, long total, int numero, int tamano) {
        this.elementos = Collections.unmodifiableList(elementos);
        this.total = total;
        this.numero = numero;
        this.tamano = tamano;
    }

    public List<T> getElementos() { return elementos; }
    public long getTotal() { return total; }
    public int getNumero() { return numero; }
    public int getTamano() { return tamano; }
    public int getTotalPaginas() { return (int) Math.max(1, (total + tamano - 1) / tamano); }
}
