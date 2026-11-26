package modelo.entity;

/** Situación de un trámite respecto a su periodo de atención (HU 1.3, 1.4). No se guarda: se calcula. */
public enum DisponibilidadTramite {
    DISPONIBLE("Disponible"),
    AUN_NO_ABRE("Próximamente"),
    PERIODO_CONCLUIDO("Periodo cerrado"),
    INACTIVO("No disponible");

    private final String etiqueta;

    DisponibilidadTramite(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
