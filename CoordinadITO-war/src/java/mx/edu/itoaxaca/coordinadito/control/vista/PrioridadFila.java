package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import modelo.entity.Tramite;

/** Una fila de prioridades.xhtml: prioridad actual del trámite y la elegida (aún sin guardar). */
public class PrioridadFila implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String codigo;
    private final String nombre;
    private final String prioridad;
    private final long abiertas;
    private String nueva;

    public PrioridadFila(Tramite t, long abiertas) {
        id = t.getId().toString();
        codigo = t.getClave();
        nombre = t.getNombre();
        prioridad = t.getPrioridad().name();
        this.abiertas = abiertas;
    }

    public boolean isCambiada() {
        return nueva != null && !nueva.isEmpty() && !nueva.equals(prioridad);
    }

    public String getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getPrioridad() { return prioridad; }
    public long getAbiertas() { return abiertas; }
    public String getNueva() { return nueva; }
    public void setNueva(String nueva) { this.nueva = nueva; }
}
