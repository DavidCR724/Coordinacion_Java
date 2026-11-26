package modelo.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estado de una solicitud (tipo ENUM ESTADO_SOLICITUD).
 * Las transiciones permitidas viven aquí para que el servicio y la vista usen la misma regla.
 *
 * ENVIADA     → PROCESANDO, ESPERA, RECHAZADA
 * PROCESANDO  → ESPERA, FINALIZADA, RECHAZADA
 * ESPERA      → PROCESANDO, RECHAZADA
 * FINALIZADA y RECHAZADA son finales.
 */
public enum EstadoSolicitud {
    ENVIADA("Recibida"),
    PROCESANDO("En revisión"),
    RECHAZADA("Rechazada"),
    FINALIZADA("Finalizada"),
    ESPERA("Requiere información");

    private final String etiqueta;

    EstadoSolicitud(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }

    /** Abierta = todavía la puede atender coordinación. */
    public boolean isAbierto() {
        return this == ENVIADA || this == PROCESANDO || this == ESPERA;
    }

    public boolean isFinal() {
        return !isAbierto();
    }

    public Set<EstadoSolicitud> siguientes() {
        switch (this) {
            case ENVIADA:    return EnumSet.of(PROCESANDO, ESPERA, RECHAZADA);
            case PROCESANDO: return EnumSet.of(ESPERA, FINALIZADA, RECHAZADA);
            case ESPERA:     return EnumSet.of(PROCESANDO, RECHAZADA);
            default:         return EnumSet.noneOf(EstadoSolicitud.class);
        }
    }

    public boolean puedePasarA(EstadoSolicitud destino) {
        return destino != null && siguientes().contains(destino);
    }

    public static Set<EstadoSolicitud> abiertos() {
        return EnumSet.of(ENVIADA, PROCESANDO, ESPERA);
    }
}
