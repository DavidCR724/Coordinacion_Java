package modelo.entity;

/** De dónde salen la carrera y el semestre de un estudiante (tipo ENUM ORIGEN_DATOS, HU 6.4 y 6.5). */
public enum OrigenDatos {
    AUTODECLARADO("Autodeclarado", "Lo capturaste en tu primer acceso; coordinación puede validarlo."),
    PADRON("Padrón institucional", "Viene del padrón oficial que cargan coordinación o jefatura."),
    VALIDADO("Validado por coordinación", "Coordinación revisó y confirmó estos datos.");

    private final String etiqueta;
    private final String explicacion;

    OrigenDatos(String etiqueta, String explicacion) {
        this.etiqueta = etiqueta;
        this.explicacion = explicacion;
    }

    public String getEtiqueta() { return etiqueta; }
    public String getExplicacion() { return explicacion; }
}
