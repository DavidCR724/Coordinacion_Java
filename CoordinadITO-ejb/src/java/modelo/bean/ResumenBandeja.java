package modelo.bean;

import java.io.Serializable;
import java.util.Map;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Prioridad;

/** Contadores del encabezado de la bandeja. */
public class ResumenBandeja implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<EstadoSolicitud, Long> porEstado;
    private final Map<Prioridad, Long> abiertasPorPrioridad;

    public ResumenBandeja(Map<EstadoSolicitud, Long> porEstado, Map<Prioridad, Long> abiertasPorPrioridad) {
        this.porEstado = porEstado;
        this.abiertasPorPrioridad = abiertasPorPrioridad;
    }

    public long getNuevas() { return valor(porEstado.get(EstadoSolicitud.ENVIADA)); }
    public long getEnRevision() { return valor(porEstado.get(EstadoSolicitud.PROCESANDO)); }
    public long getRequierenInformacion() { return valor(porEstado.get(EstadoSolicitud.ESPERA)); }
    public long getPrioridadAlta() {
        return valor(abiertasPorPrioridad.get(Prioridad.ALTA)) + valor(abiertasPorPrioridad.get(Prioridad.CRITICA));
    }
    public Map<EstadoSolicitud, Long> getPorEstado() { return porEstado; }

    private static long valor(Long l) { return l == null ? 0 : l; }
}
