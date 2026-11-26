package modelo.bean;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Prioridad;

/**
 * Criterios de la bandeja (HU 3.4). Todos son opcionales y se combinan con AND.
 * Sin estados = solo abiertas. Las fechas se interpretan en la zona del ITO.
 */
public class FiltroBandeja implements Serializable {

    private static final long serialVersionUID = 1L;

    private Set<EstadoSolicitud> estados = EnumSet.noneOf(EstadoSolicitud.class);
    private UUID idTramite;
    private Prioridad prioridadMinima;
    private LocalDate desde;
    private LocalDate hasta;
    private String texto;
    /** HU 3.1: false = por prioridad (lo normal); true = solo por fecha de registro. */
    private boolean porFecha;
    private int pagina = 0;
    private int tamano = 20;

    public Set<EstadoSolicitud> getEstados() { return estados; }
    public void setEstados(Set<EstadoSolicitud> estados) { this.estados = estados; }
    public UUID getIdTramite() { return idTramite; }
    public void setIdTramite(UUID idTramite) { this.idTramite = idTramite; }
    public Prioridad getPrioridadMinima() { return prioridadMinima; }
    public void setPrioridadMinima(Prioridad prioridadMinima) { this.prioridadMinima = prioridadMinima; }
    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }
    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta = hasta; }
    public boolean isPorFecha() { return porFecha; }
    public void setPorFecha(boolean porFecha) { this.porFecha = porFecha; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public int getPagina() { return pagina; }
    public void setPagina(int pagina) { this.pagina = Math.max(0, pagina); }
    public int getTamano() { return tamano; }
    public void setTamano(int tamano) { this.tamano = Math.max(1, Math.min(100, tamano)); }

    /** Todas las solicitudes (abiertas y cerradas), hasta {@code tamano} filas. */
    public static FiltroBandeja todas(int tamano) {
        FiltroBandeja f = new FiltroBandeja();
        f.setEstados(EnumSet.allOf(EstadoSolicitud.class));
        f.setTamano(tamano);
        return f;
    }

    public void limpiar() {
        estados = EnumSet.noneOf(EstadoSolicitud.class);
        idTramite = null;
        prioridadMinima = null;
        desde = null;
        hasta = null;
        texto = null;
        pagina = 0;
    }
}
