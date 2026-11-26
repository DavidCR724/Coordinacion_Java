package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** «ORM persistable» Tabla RESPUESTA_CAMPO: lo que el alumno capturó en un campo (HU 1.2). */
@Entity
@Table(name = "RESPUESTA_CAMPO")
public class RespuestaCampo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_RESPUESTA", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_SOLICITUD", nullable = false, updatable = false)
    private Solicitud solicitud;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_CAMPO", nullable = false, updatable = false)
    private CampoFormulario campo;

    @Column(name = "VALOR")
    private String valor;

    protected RespuestaCampo() { }

    RespuestaCampo(Solicitud solicitud, CampoFormulario campo, String valor) {
        this.solicitud = solicitud;
        this.campo = campo;
        this.valor = valor;
    }

    public UUID getId() { return id; }
    public Solicitud getSolicitud() { return solicitud; }
    public CampoFormulario getCampo() { return campo; }
    public String getValor() { return valor; }

    @Override public boolean equals(Object o) { return o instanceof RespuestaCampo && Objects.equals(id, ((RespuestaCampo) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
