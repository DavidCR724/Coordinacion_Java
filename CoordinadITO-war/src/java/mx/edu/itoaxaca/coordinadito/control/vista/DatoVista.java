package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;

/** Par etiqueta / valor de comp-dato-revision. */
public class DatoVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String etiqueta;
    private final String valor;
    private final boolean completo;

    public DatoVista(String etiqueta, String valor, boolean completo) {
        this.etiqueta = etiqueta;
        this.valor = valor;
        this.completo = completo;
    }

    public String getEtiqueta() { return etiqueta; }
    public String getValor() { return valor; }
    /** true = ocupa todo el ancho (textos largos). */
    public boolean isCompleto() { return completo; }
}
