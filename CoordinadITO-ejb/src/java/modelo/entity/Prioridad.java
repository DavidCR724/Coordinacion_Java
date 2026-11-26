package modelo.entity;

/**
 * Prioridad (tipo ENUM PRIORIDAD). El orden importa: BAJA &lt; MEDIA &lt; ALTA &lt; CRITICA,
 * igual que en PostgreSQL, así que ORDER BY y compareTo() dan el mismo resultado.
 */
public enum Prioridad {
    BAJA("Baja"),
    MEDIA("Media"),
    ALTA("Alta"),
    CRITICA("Crítica");

    private final String etiqueta;

    Prioridad(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }

    public boolean esMayorQue(Prioridad otra) {
        return otra == null || compareTo(otra) > 0;
    }
}
