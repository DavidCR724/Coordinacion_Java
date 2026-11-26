package modelo.entity;

/** Acciones que quedan en la bitácora de auditoría (columna ACCION, HU 8.2). */
public enum Accion {
    ACCESO("Inicio de sesión"),
    ACCESO_FALLIDO("Intento de acceso fallido"),
    CODIGO_ACCESO("Envío de código de acceso por correo"),
    PRIMER_ACCESO("Alta por primer acceso"),
    ALTA_CUENTA("Alta de cuenta de personal"),
    CAMBIO_ROL("Cambio de rol o carreras"),
    CAMBIO_ESTADO_CUENTA("Activación o baja de cuenta"),
    CORREGIR_ALUMNO("Corrección de datos de alumno"),
    CARGAR_PADRON("Carga de padrón"),
    EXPORTAR_HISTORIAL("Exportación del historial"),
    CONFIGURAR_TRAMITE("Cambio en el catálogo de trámites");

    private final String etiqueta;

    Accion(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
