package modelo.bean;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Solicitud;

/** Resultados del panel de seguimiento (HU 7.1 a 7.3). Clases de solo lectura. */
public final class Seguimiento {

    private Seguimiento() { }

    /** HU 7.1: conteo de un tipo de trámite (o de una carrera) desglosado por estado. */
    public static class Fila implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String nombre;
        private final Map<EstadoSolicitud, Long> porEstado;

        public Fila(String nombre, Map<EstadoSolicitud, Long> porEstado) {
            this.nombre = nombre;
            this.porEstado = porEstado;
        }

        public String getNombre() { return nombre; }
        public long getRecibidas() { return porEstado.getOrDefault(EstadoSolicitud.ENVIADA, 0L); }
        public long getEnRevision() { return porEstado.getOrDefault(EstadoSolicitud.PROCESANDO, 0L); }
        public long getEnEspera() { return porEstado.getOrDefault(EstadoSolicitud.ESPERA, 0L); }
        public long getFinalizadas() { return porEstado.getOrDefault(EstadoSolicitud.FINALIZADA, 0L); }
        public long getRechazadas() { return porEstado.getOrDefault(EstadoSolicitud.RECHAZADA, 0L); }
        public long getAbiertas() { return getRecibidas() + getEnRevision() + getEnEspera(); }
        public long getTotal() { return getAbiertas() + getFinalizadas() + getRechazadas(); }
    }

    /** HU 7.1: volumen del periodo por tipo de trámite y por carrera; {@code total} suma todas las filas. */
    public static class Volumen implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<Fila> porTramite;
        private final List<Fila> porCarrera;
        private final Fila total;

        public Volumen(List<Fila> porTramite, List<Fila> porCarrera, Fila total) {
            this.porTramite = porTramite;
            this.porCarrera = porCarrera;
            this.total = total;
        }

        public List<Fila> getPorTramite() { return porTramite; }
        public List<Fila> getPorCarrera() { return porCarrera; }
        public Fila getTotal() { return total; }
    }

    /** HU 7.2: tiempo de resolución de un tipo de trámite (solo finalizadas o rechazadas). */
    public static class Tiempo implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String tramite;
        private final int resueltas;
        private final double promedioDias;
        private final double minimoDias;
        private final double maximoDias;
        private final int diasEsperados;
        private final int dentroDeTiempo;

        public Tiempo(String tramite, int resueltas, double promedioDias, double minimoDias, double maximoDias,
                      int diasEsperados, int dentroDeTiempo) {
            this.tramite = tramite;
            this.resueltas = resueltas;
            this.promedioDias = promedioDias;
            this.minimoDias = minimoDias;
            this.maximoDias = maximoDias;
            this.diasEsperados = diasEsperados;
            this.dentroDeTiempo = dentroDeTiempo;
        }

        public String getTramite() { return tramite; }
        public int getResueltas() { return resueltas; }
        public double getPromedioDias() { return promedioDias; }
        public double getMinimoDias() { return minimoDias; }
        public double getMaximoDias() { return maximoDias; }
        public int getDiasEsperados() { return diasEsperados; }
        /** Cuántas se resolvieron dentro de los días esperados. */
        public int getDentroDeTiempo() { return dentroDeTiempo; }
        public int getPorcentajeEnTiempo() { return resueltas == 0 ? 0 : Math.round(100f * dentroDeTiempo / resueltas); }
    }

    /** HU 7.3: solicitud abierta que rebasó el tiempo esperado de su trámite. */
    public static class Rezago implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Solicitud solicitud;
        private final long dias;

        public Rezago(Solicitud solicitud, long dias) {
            this.solicitud = solicitud;
            this.dias = dias;
        }

        public Solicitud getSolicitud() { return solicitud; }
        public long getDias() { return dias; }
        public long getExceso() { return dias - solicitud.getTramite().getDiasEsperados(); }
    }
}
