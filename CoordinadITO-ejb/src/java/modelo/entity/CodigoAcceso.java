package modelo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * «ORM persistable» Tabla CODIGO_ACCESO (bd/07): un código de 6 dígitos enviado por correo para entrar.
 * Solo guarda la HUELLA (SHA-256 de sal + código); el código en claro solo existe en el correo.
 */
@Entity
@Table(name = "CODIGO_ACCESO")
public class CodigoAcceso implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_CODIGO", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "CORREO", nullable = false, updatable = false)
    private String correo;

    @Column(name = "HUELLA", nullable = false, updatable = false)
    private String huella;

    @Column(name = "SAL", nullable = false, updatable = false)
    private String sal;

    @Column(name = "CREADO_EN", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = Fechas.ahora();

    @Column(name = "VENCE_EN", nullable = false, updatable = false)
    private OffsetDateTime venceEn;

    @Column(name = "USADO_EN")
    private OffsetDateTime usadoEn;

    @Column(name = "INTENTOS", nullable = false)
    private short intentos;

    /** Columna INET. */
    @Column(name = "IP_ORIGEN", updatable = false)
    private String ipOrigen;

    protected CodigoAcceso() { }

    public CodigoAcceso(String correo, String huella, String sal, int minutosDeVigencia, String ipOrigen) {
        this.correo = correo;
        this.huella = huella;
        this.sal = sal;
        this.venceEn = creadoEn.plusMinutes(minutosDeVigencia);
        this.ipOrigen = ipOrigen;
    }

    public boolean isVencido(OffsetDateTime ahora) { return !ahora.isBefore(venceEn); }
    public boolean isUsado() { return usadoEn != null; }

    public void sumarIntento() { intentos++; }
    public void marcarUsado() { usadoEn = Fechas.ahora(); }

    public UUID getId() { return id; }
    public String getCorreo() { return correo; }
    public String getHuella() { return huella; }
    public String getSal() { return sal; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public OffsetDateTime getVenceEn() { return venceEn; }
    public OffsetDateTime getUsadoEn() { return usadoEn; }
    public int getIntentos() { return intentos; }
    public String getIpOrigen() { return ipOrigen; }

    @Override public boolean equals(Object o) { return o instanceof CodigoAcceso && Objects.equals(id, ((CodigoAcceso) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
