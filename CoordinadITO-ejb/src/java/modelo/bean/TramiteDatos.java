package modelo.bean;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import modelo.entity.CampoFormulario;
import modelo.entity.Prioridad;
import modelo.entity.Tramite;

/**
 * Un trámite con su formulario, tal como lo edita jefatura (HU 8.3). id null = trámite nuevo.
 * El WAR enlaza sus campos directamente al formulario; el EJB los valida al guardar.
 */
public class TramiteDatos implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID id;
    private String clave;
    private String nombre;
    private String descripcion;
    private String requisitos;
    private Prioridad prioridad = Prioridad.MEDIA;
    private boolean documentoRequerido;
    private boolean colectivo;
    private LocalDate inicio;
    private LocalDate fin;
    private Integer diasEsperados = 5;
    private boolean activo = true;
    private final List<CampoDatos> campos = new ArrayList<>();

    public static TramiteDatos de(Tramite t) {
        TramiteDatos d = new TramiteDatos();
        d.id = t.getId();
        d.clave = t.getClave();
        d.nombre = t.getNombre();
        d.descripcion = t.getDescripcion();
        d.requisitos = t.getRequisitos();
        d.prioridad = t.getPrioridad();
        d.documentoRequerido = t.isDocumentoRequerido();
        d.colectivo = t.isColectivo();
        d.inicio = t.getInicioPeriodo();
        d.fin = t.getFinPeriodo();
        d.diasEsperados = (int) t.getDiasEsperados();
        d.activo = t.isActivo();
        for (CampoFormulario c : t.getCampos()) {
            d.campos.add(CampoDatos.de(c));
        }
        return d;
    }

    public boolean isNuevo() { return id == null; }
    public UUID getId() { return id; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getRequisitos() { return requisitos; }
    public void setRequisitos(String requisitos) { this.requisitos = requisitos; }
    public Prioridad getPrioridad() { return prioridad; }
    public void setPrioridad(Prioridad prioridad) { this.prioridad = prioridad; }
    public boolean isDocumentoRequerido() { return documentoRequerido; }
    public void setDocumentoRequerido(boolean documentoRequerido) { this.documentoRequerido = documentoRequerido; }
    public boolean isColectivo() { return colectivo; }
    public void setColectivo(boolean colectivo) { this.colectivo = colectivo; }
    public LocalDate getInicio() { return inicio; }
    public void setInicio(LocalDate inicio) { this.inicio = inicio; }
    public LocalDate getFin() { return fin; }
    public void setFin(LocalDate fin) { this.fin = fin; }
    public Integer getDiasEsperados() { return diasEsperados; }
    public void setDiasEsperados(Integer diasEsperados) { this.diasEsperados = diasEsperados; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public List<CampoDatos> getCampos() { return campos; }
}
