package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import modelo.entity.CampoFormulario;
import modelo.entity.DisponibilidadTramite;
import modelo.entity.Tramite;

/**
 * Un trámite del catálogo con los nombres de propiedad que ya usan las vistas
 * (codigo, apertura, cierre, etiqueta, requisitos...).
 */
public class TramiteVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String codigo;
    private final String nombre;
    private final String descripcion;
    private final String prioridad;
    private final LocalDate inicio;
    private final LocalDate fin;
    private final String codigoDisponibilidad;
    private final boolean activo;
    private final boolean documentoRequerido;
    private final boolean colectivo;
    private final int diasEsperados;
    private final List<String> requisitos;
    private final List<CampoVista> campos;
    private long abiertas;

    /** Sin campos (catálogo, periodos, prioridades). */
    public TramiteVista(Tramite t) {
        this(t, false);
    }

    public TramiteVista(Tramite t, boolean conCampos) {
        id = t.getId().toString();
        codigo = t.getClave();
        nombre = t.getNombre();
        descripcion = t.getDescripcion() == null ? "" : t.getDescripcion();
        prioridad = t.getPrioridad().name();
        inicio = t.getInicioPeriodo();
        fin = t.getFinPeriodo();
        activo = t.isActivo();
        documentoRequerido = t.isDocumentoRequerido();
        colectivo = t.isColectivo();
        diasEsperados = t.getDiasEsperados();
        codigoDisponibilidad = codigo(t.getDisponibilidad());
        List<String> rs = new ArrayList<>();
        if (t.getRequisitos() != null) {
            for (String linea : t.getRequisitos().split("\\r?\\n")) {
                String r = linea.replaceFirst("^\\s*[-•*]\\s*", "").trim();
                if (!r.isEmpty()) {
                    rs.add(r);
                }
            }
        }
        requisitos = Collections.unmodifiableList(rs);
        List<CampoVista> cs = new ArrayList<>();
        if (conCampos) {
            for (CampoFormulario c : t.getCamposActivos()) {
                cs.add(new CampoVista(c));
            }
        }
        campos = Collections.unmodifiableList(cs);
    }

    private static String codigo(DisponibilidadTramite d) {
        switch (d) {
            case DISPONIBLE: return "disponible";
            case AUN_NO_ABRE: return "proximo";
            case PERIODO_CONCLUIDO: return "cerrado";
            default: return "inactivo";
        }
    }

    public String getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public String getPrioridad() { return prioridad; }
    public boolean isActivo() { return activo; }
    public boolean isDocumentoRequerido() { return documentoRequerido; }
    /** disponible | proximo | cerrado | inactivo */
    public String getCodigoDisponibilidad() { return codigoDisponibilidad; }
    public boolean isDisponible() { return "disponible".equals(codigoDisponibilidad); }

    public String getEtiqueta() {
        switch (codigoDisponibilidad) {
            case "disponible": return "Disponible";
            case "proximo": return "Próximamente";
            case "cerrado": return "Periodo cerrado";
            default: return "Inactivo";
        }
    }

    /** success, warning o danger para el p:tag de disponibilidad. */
    public String getSeveridad() {
        switch (codigoDisponibilidad) {
            case "disponible": return "success";
            case "proximo": return "warning";
            default: return "danger";
        }
    }

    /** Texto para el aviso de "trámite no disponible". */
    public String getMensajeDisponibilidad() {
        switch (codigoDisponibilidad) {
            case "proximo": return "El periodo de atención comienza el " + Formato.fecha(inicio) + ".";
            case "cerrado": return "El periodo concluyó el " + Formato.fecha(fin) + ". La siguiente apertura está pendiente de publicación.";
            case "inactivo": return "Este trámite no está disponible por ahora.";
            default: return "";
        }
    }

    public String getApertura() { return inicio == null ? "Sin fecha de apertura" : Formato.fecha(inicio); }
    public String getCierre() { return fin == null ? "sin fecha de cierre" : Formato.fecha(fin); }
    public String getAperturaIso() { return Formato.iso(inicio); }
    public String getCierreIso() { return Formato.iso(fin); }
    public LocalDate getInicio() { return inicio; }
    public LocalDate getFin() { return fin; }
    public List<String> getRequisitos() { return requisitos; }
    /** Los dos primeros requisitos, para la tarjeta del catálogo. */
    public List<String> getRequisitosResumen() { return requisitos.subList(0, Math.min(2, requisitos.size())); }
    public List<CampoVista> getCampos() { return campos; }
    public long getAbiertas() { return abiertas; }
    public boolean isColectivo() { return colectivo; }
    public int getDiasEsperados() { return diasEsperados; }
    public void setAbiertas(long abiertas) { this.abiertas = abiertas; }
}
