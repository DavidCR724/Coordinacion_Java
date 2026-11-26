package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** «ORM persistable» Tabla CAMPO_FORMULARIO: un campo del formulario propio de un trámite (HU 1.2). */
@Entity
@Table(name = "CAMPO_FORMULARIO")
public class CampoFormulario implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_CAMPO", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_TRAMITE", nullable = false)
    private Tramite tramite;

    @Column(name = "ETIQUETA", nullable = false)
    private String etiqueta;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false)
    private TipoCampo tipo = TipoCampo.TEXTO;

    @Column(name = "OBLIGATORIO", nullable = false)
    private boolean obligatorio = true;

    @Column(name = "ORDEN", nullable = false)
    private short orden;

    @Column(name = "AYUDA")
    private String ayuda;

    @Column(name = "LONGITUD_MAX")
    private Integer longitudMax;

    /** Solo SELECCION: una opción por línea. */
    @Column(name = "OPCIONES")
    private String opciones;

    @Column(name = "ACTIVO", nullable = false)
    private boolean activo = true;

    public CampoFormulario() { }

    public CampoFormulario(String etiqueta, TipoCampo tipo, boolean obligatorio, int orden) {
        this.etiqueta = etiqueta;
        this.tipo = tipo;
        this.obligatorio = obligatorio;
        this.orden = (short) orden;
    }

    public List<String> getListaOpciones() {
        List<String> r = new ArrayList<>();
        if (opciones != null) {
            for (String linea : opciones.split("\\r?\\n")) {
                if (!linea.trim().isEmpty()) {
                    r.add(linea.trim());
                }
            }
        }
        return r;
    }

    public UUID getId() { return id; }
    public Tramite getTramite() { return tramite; }
    void setTramite(Tramite tramite) { this.tramite = tramite; }
    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }
    public TipoCampo getTipo() { return tipo; }
    public void setTipo(TipoCampo tipo) { this.tipo = tipo; }
    public boolean isObligatorio() { return obligatorio; }
    public void setObligatorio(boolean obligatorio) { this.obligatorio = obligatorio; }
    public short getOrden() { return orden; }
    public void setOrden(short orden) { this.orden = orden; }
    public String getAyuda() { return ayuda; }
    public void setAyuda(String ayuda) { this.ayuda = ayuda; }
    public Integer getLongitudMax() { return longitudMax; }
    public void setLongitudMax(Integer longitudMax) { this.longitudMax = longitudMax; }
    public String getOpciones() { return opciones; }
    public void setOpciones(String opciones) { this.opciones = opciones; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    @Override public boolean equals(Object o) { return o instanceof CampoFormulario && Objects.equals(id, ((CampoFormulario) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
