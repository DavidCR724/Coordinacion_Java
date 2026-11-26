package modelo.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * «ORM persistable» Tabla SOLICITUD: el ticket. Un trámite pedido por un alumno.
 *
 * Los cambios de estado no se hacen con setEstado desde fuera: el servicio usa
 * {@link #cambiarEstado}, que valida la transición y deja la entrada en la bitácora.
 */
@Entity
@Table(name = "SOLICITUD")
public class Solicitud implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "ID_SOLICITUD", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    /** Consecutivo visible (ITO-####). Lo genera la base (IDENTITY). */
    @Column(name = "FOLIO", insertable = false, updatable = false)
    private Integer folio;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_TRAMITE", nullable = false, updatable = false)
    private Tramite tramite;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_SOLICITANTE", nullable = false, updatable = false)
    private Usuario solicitante;

    @ManyToOne
    @JoinColumn(name = "ID_ASIGNADO")
    private Usuario asignado;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoSolicitud estado = EstadoSolicitud.ENVIADA;

    @Column(name = "PETICION")
    private String peticion;

    @Column(name = "RESOLUCION")
    private String resolucion;

    @Column(name = "FECHA_REGISTRO", nullable = false, updatable = false)
    private OffsetDateTime fechaRegistro = Fechas.ahora();

    /** La mantiene también un disparador de la base en cada UPDATE. */
    @Column(name = "FECHA_ACTUALIZACION", nullable = false)
    private OffsetDateTime fechaActualizacion = fechaRegistro;

    /** Carrera del alumno al registrar: decide qué coordinación la ve. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "ID_CARRERA", nullable = false, updatable = false)
    private Carrera carrera;

    /** Nace con la del trámite y solo puede subir (HU 3.3). */
    @Enumerated(EnumType.STRING)
    @Column(name = "PRIORIDAD", nullable = false)
    private Prioridad prioridad;

    @ManyToOne
    @JoinColumn(name = "ID_MATERIA")
    private Materia materia;

    @Column(name = "FECHA_CIERRE")
    private OffsetDateTime fechaCierre;

    /** Coordinación pidió atender al alumno en persona (HU 9.2). */
    @Column(name = "CITA_REQUERIDA", nullable = false)
    private boolean citaRequerida;

    /** Alumnos que se sumaron a una apertura de grupo (HU 4.2). */
    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha ASC")
    private List<Adhesion> adhesiones = new ArrayList<>();

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RespuestaCampo> respuestas = new ArrayList<>();

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.PERSIST)
    @OrderBy("fecha ASC")
    private List<EntradaBitacora> bitacora = new ArrayList<>();

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.PERSIST)
    @OrderBy("fecha ASC")
    private List<MensajeSolicitud> mensajes = new ArrayList<>();

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.PERSIST)
    @OrderBy("fechaSubida ASC")
    private List<DocumentoAdjunto> adjuntos = new ArrayList<>();

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.PERSIST)
    @OrderBy("fecha ASC")
    private List<CambioPrioridad> cambiosPrioridad = new ArrayList<>();

    protected Solicitud() { }

    /** Solicitud nueva: copia carrera del alumno y prioridad del trámite, y abre la bitácora. */
    public Solicitud(Tramite tramite, Usuario solicitante, String peticion) {
        this.tramite = tramite;
        this.solicitante = solicitante;
        this.carrera = solicitante.getCarrera();
        this.prioridad = tramite.getPrioridad();
        this.peticion = peticion;
        bitacora.add(new EntradaBitacora(this, null, EstadoSolicitud.ENVIADA, solicitante, "Solicitud registrada"));
    }

    /** HU 4.1: apertura de grupo. Quien la pide cuenta como el primer interesado. */
    public static Solicitud aperturaDeGrupo(Tramite tramite, Usuario solicitante, Materia materia, String motivo) {
        Solicitud s = new Solicitud(tramite, solicitante, motivo);
        s.materia = materia;
        return s;
    }

    // ---------- apertura de grupo (HU 4.2, 4.3) ----------

    public boolean isColectiva() {
        return materia != null;
    }

    public boolean tieneAdherido(Usuario alumno) {
        for (Adhesion a : adhesiones) {
            if (a.getAlumno().equals(alumno)) {
                return true;
            }
        }
        return false;
    }

    /** ¿El alumno ya está en la lista (porque la pidió o porque se adhirió)? */
    public boolean esInteresado(Usuario alumno) {
        return solicitante.equals(alumno) || tieneAdherido(alumno);
    }

    public void adherir(Usuario alumno) {
        if (esInteresado(alumno)) {
            throw new IllegalStateException("Ya está en la lista.");
        }
        adhesiones.add(new Adhesion(this, alumno));
    }

    public void retirarAdhesion(Usuario alumno) {
        adhesiones.removeIf(a -> a.getAlumno().equals(alumno));
    }

    /** Quien la registró más los adheridos: todos reciben los avisos de cambio (HU 4.3). */
    public List<Usuario> getInteresados() {
        List<Usuario> r = new ArrayList<>();
        r.add(solicitante);
        for (Adhesion a : adhesiones) {
            r.add(a.getAlumno());
        }
        return r;
    }

    /** Demanda real de la materia (HU 4.4). */
    public int getNumeroInteresados() {
        return 1 + adhesiones.size();
    }

    /**
     * Cambia el estado y registra la constancia. Lanza IllegalStateException si la transición
     * no está permitida; el servicio la traduce a un mensaje para la vista.
     */
    public EntradaBitacora cambiarEstado(EstadoSolicitud nuevo, Usuario actor, String comentario) {
        if (!estado.puedePasarA(nuevo)) {
            throw new IllegalStateException("No se puede pasar de " + estado.getEtiqueta() + " a " + nuevo.getEtiqueta());
        }
        EntradaBitacora entrada = new EntradaBitacora(this, estado, nuevo, actor, comentario);
        bitacora.add(entrada);
        estado = nuevo;
        fechaActualizacion = Fechas.ahora();
        fechaCierre = nuevo.isFinal() ? fechaActualizacion : null;
        return entrada;
    }

    public CambioPrioridad elevarPrioridad(Prioridad nueva, Usuario actor, String motivo) {
        if (!nueva.esMayorQue(prioridad)) {
            throw new IllegalStateException("La prioridad solo puede subir");
        }
        CambioPrioridad cambio = new CambioPrioridad(this, prioridad, nueva, actor, motivo);
        cambiosPrioridad.add(cambio);
        prioridad = nueva;
        fechaActualizacion = Fechas.ahora();
        return cambio;
    }

    public MensajeSolicitud agregarMensaje(Usuario autor, TipoMensaje tipo, String contenido, MensajeSolicitud respondeA) {
        MensajeSolicitud m = new MensajeSolicitud(this, autor, tipo, contenido, respondeA);
        mensajes.add(m);
        return m;
    }

    public void agregarRespuesta(CampoFormulario campo, String valor) {
        respuestas.add(new RespuestaCampo(this, campo, valor));
    }

    public void agregarAdjunto(DocumentoAdjunto doc) {
        adjuntos.add(doc);
    }

    /** Última petición de información sin contestar, si la hay. */
    public MensajeSolicitud getPeticionPendiente() {
        MensajeSolicitud ultima = null;
        for (MensajeSolicitud m : mensajes) {
            if (m.getTipo() == TipoMensaje.SOLICITUD_INFORMACION) {
                ultima = m;
            } else if (m.getTipo() == TipoMensaje.RESPUESTA && ultima != null && ultima.equals(m.getRespondeA())) {
                ultima = null;
            }
        }
        return ultima;
    }

    public String getFolioTexto() {
        return folio == null ? "ITO-(sin folio)" : String.format("ITO-%04d", folio);
    }

    public UUID getId() { return id; }
    public Integer getFolio() { return folio; }
    public Tramite getTramite() { return tramite; }
    public Usuario getSolicitante() { return solicitante; }
    public Usuario getAsignado() { return asignado; }
    public void setAsignado(Usuario asignado) { this.asignado = asignado; }
    public EstadoSolicitud getEstado() { return estado; }
    public String getPeticion() { return peticion; }
    public String getResolucion() { return resolucion; }
    public void setResolucion(String resolucion) { this.resolucion = resolucion; }
    public OffsetDateTime getFechaRegistro() { return fechaRegistro; }
    public OffsetDateTime getFechaActualizacion() { return fechaActualizacion; }
    public Carrera getCarrera() { return carrera; }
    public Prioridad getPrioridad() { return prioridad; }
    public Materia getMateria() { return materia; }
    public OffsetDateTime getFechaCierre() { return fechaCierre; }
    public boolean isCitaRequerida() { return citaRequerida; }
    public void setCitaRequerida(boolean citaRequerida) { this.citaRequerida = citaRequerida; }
    public List<Adhesion> getAdhesiones() { return adhesiones; }
    public List<RespuestaCampo> getRespuestas() { return respuestas; }
    public List<EntradaBitacora> getBitacora() { return bitacora; }
    public List<MensajeSolicitud> getMensajes() { return mensajes; }
    public List<DocumentoAdjunto> getAdjuntos() { return adjuntos; }
    public List<CambioPrioridad> getCambiosPrioridad() { return cambiosPrioridad; }

    @Override public boolean equals(Object o) { return o instanceof Solicitud && Objects.equals(id, ((Solicitud) o).id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
    @Override public String toString() { return getFolioTexto(); }
}
