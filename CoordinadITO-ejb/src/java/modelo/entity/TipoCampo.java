package modelo.entity;

/** Tipo de un campo del formulario propio de cada trámite (tipo ENUM TIPO_CAMPO, HU 1.2). */
public enum TipoCampo {
    TEXTO("Texto corto"),
    TEXTO_LARGO("Texto largo"),
    NUMERO("Número"),
    FECHA("Fecha"),
    SELECCION("Lista de opciones");

    private final String etiqueta;

    TipoCampo(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
