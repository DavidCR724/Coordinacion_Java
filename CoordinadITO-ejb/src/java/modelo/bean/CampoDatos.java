package modelo.bean;

import java.io.Serializable;
import java.util.UUID;
import modelo.entity.CampoFormulario;
import modelo.entity.TipoCampo;

/** Un campo del formulario tal como lo edita jefatura (HU 8.3). id null = campo nuevo. */
public class CampoDatos implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID id;
    private String etiqueta;
    private TipoCampo tipo = TipoCampo.TEXTO;
    private boolean obligatorio = true;
    private String ayuda;
    private Integer longitudMax;
    private String opciones;
    private boolean activo = true;

    public CampoDatos() { }

    public static CampoDatos de(CampoFormulario c) {
        CampoDatos d = new CampoDatos();
        d.id = c.getId();
        d.etiqueta = c.getEtiqueta();
        d.tipo = c.getTipo();
        d.obligatorio = c.isObligatorio();
        d.ayuda = c.getAyuda();
        d.longitudMax = c.getLongitudMax();
        d.opciones = c.getOpciones();
        d.activo = c.isActivo();
        return d;
    }

    public UUID getId() { return id; }
    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }
    public TipoCampo getTipo() { return tipo; }
    public void setTipo(TipoCampo tipo) { this.tipo = tipo; }
    public boolean isObligatorio() { return obligatorio; }
    public void setObligatorio(boolean obligatorio) { this.obligatorio = obligatorio; }
    public String getAyuda() { return ayuda; }
    public void setAyuda(String ayuda) { this.ayuda = ayuda; }
    public Integer getLongitudMax() { return longitudMax; }
    public void setLongitudMax(Integer longitudMax) { this.longitudMax = longitudMax; }
    public String getOpciones() { return opciones; }
    public void setOpciones(String opciones) { this.opciones = opciones; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
