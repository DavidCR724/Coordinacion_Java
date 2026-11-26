package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import modelo.entity.Tramite;

/** Formulario de periodo de un trámite (comp-periodo.xhtml): lo que se edita más su estado actual. */
public class PeriodoFila implements Serializable {

    private static final long serialVersionUID = 1L;

    private TramiteVista tramite;
    private String aperturaIso;
    private String cierreIso;
    private boolean activo;

    public PeriodoFila(Tramite t) {
        actualizar(t);
    }

    /** Vuelve a tomar los valores guardados (después de guardar o al descartar). */
    public final void actualizar(Tramite t) {
        tramite = new TramiteVista(t);
        aperturaIso = tramite.getAperturaIso();
        cierreIso = tramite.getCierreIso();
        activo = t.isActivo();
    }

    public TramiteVista getTramite() { return tramite; }
    public String getId() { return tramite.getId(); }
    public String getCodigo() { return tramite.getCodigo(); }
    public String getNombre() { return tramite.getNombre(); }
    public String getEtiqueta() { return tramite.getEtiqueta(); }
    public String getSeveridad() { return tramite.getSeveridad(); }
    public boolean isDisponible() { return tramite.isDisponible(); }
    public String getAperturaIso() { return aperturaIso; }
    public void setAperturaIso(String aperturaIso) { this.aperturaIso = aperturaIso; }
    public String getCierreIso() { return cierreIso; }
    public void setCierreIso(String cierreIso) { this.cierreIso = cierreIso; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
