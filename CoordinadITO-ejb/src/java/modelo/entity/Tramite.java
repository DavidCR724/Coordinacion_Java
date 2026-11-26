package modelo.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** «ORM persistable» Tabla TRAMITE: tipo de trámite del catálogo (HU 1.1, 1.3, 3.2). */
@Entity
@Table(name = "TRAMITE")
public class Tramite implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_TRAMITE", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "CLAVE", nullable = false, unique = true)
    private String clave;

    @Column(name = "NOMBRE", nullable = false)
    private String nombre;

    /** Prioridad por defecto de las solicitudes nuevas de este trámite (HU 3.2). */
    @Enumerated(EnumType.STRING)
    @Column(name = "PRIORIDAD", nullable = false)
    private Prioridad prioridad = Prioridad.MEDIA;

    @Column(name = "DESCRIPCION")
    private String descripcion;

    @Column(name = "REQUISITOS")
    private String requisitos;

    /** Exige al menos un documento adjunto (HU 1.5). */
    @Column(name = "DOC_REQ", nullable = false)
    private boolean documentoRequerido;

    /** Trámite colectivo: apertura de grupo con adhesiones (Iteración 3). */
    @Column(name = "ES_COLECTIVO", nullable = false)
    private boolean colectivo;

    @Column(name = "INICIO_PERIODO")
    private LocalDate inicioPeriodo;

    @Column(name = "FIN_PERIODO")
    private LocalDate finPeriodo;

    @Column(name = "ACTIVO", nullable = false)
    private boolean activo = true;

    /** Días en que normalmente se resuelve; al rebasarlos la solicitud es un rezago (HU 7.3). */
    @Column(name = "DIAS_ESPERADOS", nullable = false)
    private short diasEsperados = 5;

    @OneToMany(mappedBy = "tramite", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<CampoFormulario> campos = new ArrayList<>();

    /**
     * Disponibilidad en una fecha (HU 1.4). Sin fechas definidas el trámite se considera abierto
     * todo el año; con solo inicio, abierto desde ese día; con solo fin, abierto hasta ese día.
     */
    public DisponibilidadTramite disponibilidadEn(LocalDate dia) {
        if (!activo) {
            return DisponibilidadTramite.INACTIVO;
        }
        if (inicioPeriodo != null && dia.isBefore(inicioPeriodo)) {
            return DisponibilidadTramite.AUN_NO_ABRE;
        }
        if (finPeriodo != null && dia.isAfter(finPeriodo)) {
            return DisponibilidadTramite.PERIODO_CONCLUIDO;
        }
        return DisponibilidadTramite.DISPONIBLE;
    }

    public DisponibilidadTramite getDisponibilidad() {
        return disponibilidadEn(Fechas.hoy());
    }

    public boolean isDisponible() {
        return getDisponibilidad() == DisponibilidadTramite.DISPONIBLE;
    }

    /** Campos visibles del formulario, ya ordenados. */
    public List<CampoFormulario> getCamposActivos() {
        List<CampoFormulario> r = new ArrayList<>();
        for (CampoFormulario c : campos) {
            if (c.isActivo()) {
                r.add(c);
            }
        }
        return r;
    }

    public void agregarCampo(CampoFormulario campo) {
        campo.setTramite(this);
        campos.add(campo);
    }

    public UUID getId() { return id; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Prioridad getPrioridad() { return prioridad; }
    public void setPrioridad(Prioridad prioridad) { this.prioridad = prioridad; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getRequisitos() { return requisitos; }
    public void setRequisitos(String requisitos) { this.requisitos = requisitos; }
    public boolean isDocumentoRequerido() { return documentoRequerido; }
    public void setDocumentoRequerido(boolean documentoRequerido) { this.documentoRequerido = documentoRequerido; }
    public boolean isColectivo() { return colectivo; }
    public void setColectivo(boolean colectivo) { this.colectivo = colectivo; }
    public LocalDate getInicioPeriodo() { return inicioPeriodo; }
    public void setInicioPeriodo(LocalDate inicioPeriodo) { this.inicioPeriodo = inicioPeriodo; }
    public LocalDate getFinPeriodo() { return finPeriodo; }
    public void setFinPeriodo(LocalDate finPeriodo) { this.finPeriodo = finPeriodo; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public List<CampoFormulario> getCampos() { return campos; }
    public short getDiasEsperados() { return diasEsperados; }
    public void setDiasEsperados(short diasEsperados) { this.diasEsperados = diasEsperados; }

    @Override public boolean equals(Object o) { return o instanceof Tramite && Objects.equals(id, ((Tramite) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
    @Override public String toString() { return clave; }
}
