package modelo.entity;

/** Estado de una cita presencial (tipo ENUM ESTADO_CITA, HU 9.2 y 9.3). */
public enum EstadoCita {
    PROGRAMADA("Programada"),
    CANCELADA("Cancelada"),
    ASISTIO("Asistió"),
    NO_ASISTIO("No asistió");

    private final String etiqueta;

    EstadoCita(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
