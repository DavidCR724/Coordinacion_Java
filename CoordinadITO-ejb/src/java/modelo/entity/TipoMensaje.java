package modelo.entity;

/** Tipo de mensaje dentro de una solicitud (tipo ENUM TIPO_MENSAJE, HU 2.5 y 2.6). */
public enum TipoMensaje {
    /** Coordinación pide al alumno algo que falta. */
    SOLICITUD_INFORMACION,
    /** El alumno contesta en la misma solicitud. */
    RESPUESTA,
    /** Comentario libre. */
    NOTA
}
