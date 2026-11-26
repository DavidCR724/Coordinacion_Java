package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import modelo.entity.CambioPrioridad;
import modelo.entity.DocumentoAdjunto;
import modelo.entity.EntradaBitacora;
import modelo.entity.EstadoSolicitud;
import modelo.entity.MensajeSolicitud;
import modelo.entity.RespuestaCampo;
import modelo.entity.Solicitud;
import modelo.entity.TipoCampo;

/** Todo lo que muestra solicitud-detalle.xhtml de una solicitud (HU 2.3, 2.8). */
public class SolicitudDetalle implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String folio;
    private final String folioTexto;
    private final String tramite;
    private final String estudiante;
    private final String control;
    private final String carrera;
    private final String estado;
    private final String codigoEstado;
    private final String etiquetaEstado;
    private final String prioridad;
    private final String registroCompleto;
    private final String peticion;
    private final String resolucion;
    private final String fechaResolucion;
    private final String asignado;
    private final String peticionPendiente;
    private final List<DatoVista> datos = new ArrayList<>();
    private final List<EventoVista> historial = new ArrayList<>();
    private final List<MensajeVista> mensajes = new ArrayList<>();
    private final List<AdjuntoVista> adjuntos = new ArrayList<>();
    private final List<String> siguientes = new ArrayList<>();

    private final boolean citaRequerida;
    private final boolean colectiva;

    public SolicitudDetalle(Solicitud s) {
        citaRequerida = s.isCitaRequerida();
        colectiva = s.isColectiva();
        folio = String.valueOf(s.getFolio());
        folioTexto = s.getFolioTexto();
        tramite = s.getTramite().getNombre();
        estudiante = s.getSolicitante().getNombreCompleto();
        control = s.getSolicitante().getNumControl() == null ? "" : s.getSolicitante().getNumControl();
        carrera = s.getCarrera() == null ? "" : s.getCarrera().getNombre();
        estado = s.getEstado().name();
        codigoEstado = Formato.codigo(s.getEstado());
        etiquetaEstado = s.getEstado().getEtiqueta();
        prioridad = s.getPrioridad().name();
        registroCompleto = Formato.fechaHora(s.getFechaRegistro());
        peticion = s.getPeticion();
        resolucion = s.getResolucion();
        fechaResolucion = Formato.fechaHora(s.getFechaCierre());
        asignado = s.getAsignado() == null ? null : s.getAsignado().getNombreCompleto();
        MensajeSolicitud pendiente = s.getPeticionPendiente();
        peticionPendiente = pendiente == null || s.getEstado() != EstadoSolicitud.ESPERA ? null : pendiente.getContenido();

        List<RespuestaCampo> rs = new ArrayList<>(s.getRespuestas());
        rs.sort((a, b) -> Short.compare(a.getCampo().getOrden(), b.getCampo().getOrden()));
        for (RespuestaCampo r : rs) {
            datos.add(new DatoVista(r.getCampo().getEtiqueta(), r.getValor(),
                    r.getCampo().getTipo() == TipoCampo.TEXTO_LARGO || r.getValor().length() > 60));
        }
        if (peticion != null && !peticion.isEmpty()) {
            datos.add(new DatoVista("Explicación del alumno", peticion, true));
        }
        // Bitácora: de la más reciente a la más antigua.
        List<EntradaBitacora> bs = new ArrayList<>(s.getBitacora());
        bs.sort((a, b) -> b.getFecha().compareTo(a.getFecha()));
        for (EntradaBitacora e : bs) {
            historial.add(new EventoVista(e));
        }
        List<MensajeSolicitud> ms = new ArrayList<>(s.getMensajes());
        ms.sort((a, b) -> a.getFecha().compareTo(b.getFecha()));
        for (MensajeSolicitud m : ms) {
            mensajes.add(new MensajeVista(m));
        }
        for (DocumentoAdjunto d : s.getAdjuntos()) {
            adjuntos.add(new AdjuntoVista(d));
        }
        // Una sola línea de tiempo: estados y elevaciones de prioridad, la más reciente primero.
        for (CambioPrioridad c : s.getCambiosPrioridad()) {
            historial.add(new EventoVista(c));
        }
        historial.sort((a, b) -> b.getMomento().compareTo(a.getMomento()));
        // Estados a los que coordinación puede pasarla desde el panel «Atender» (ESPERA va por «Pedir información»).
        for (EstadoSolicitud e : new EstadoSolicitud[]{EstadoSolicitud.PROCESANDO, EstadoSolicitud.FINALIZADA, EstadoSolicitud.RECHAZADA}) {
            if (s.getEstado().puedePasarA(e)) {
                siguientes.add(Formato.codigo(e));
            }
        }
    }

    public String getFolio() { return folio; }
    /** Coordinación pidió ver al alumno en persona (HU 9.2). */
    public boolean isCitaRequerida() { return citaRequerida; }
    /** Es una apertura de grupo (HU 4.x): el detalle enlaza a la lista de interesados. */
    public boolean isColectiva() { return colectiva; }
    public String getFolioTexto() { return folioTexto; }
    public String getTramite() { return tramite; }
    public String getEstudiante() { return estudiante; }
    public String getControl() { return control; }
    public String getCarrera() { return carrera; }
    /** Nombre del ENUM: ENVIADA, PROCESANDO, ESPERA, FINALIZADA, RECHAZADA. */
    public String getEstado() { return estado; }
    public String getCodigoEstado() { return codigoEstado; }
    public String getEtiquetaEstado() { return etiquetaEstado; }
    public String getPrioridad() { return prioridad; }
    public String getRegistroCompleto() { return registroCompleto; }
    public String getResolucion() { return resolucion; }
    public String getFechaResolucion() { return fechaResolucion; }
    public String getAsignado() { return asignado; }
    public boolean isCerrada() { return "completed".equals(codigoEstado) || "rejected".equals(codigoEstado); }
    public boolean isEnEspera() { return "waiting".equals(codigoEstado); }
    public String getPeticionPendiente() { return peticionPendiente; }
    public List<DatoVista> getDatos() { return Collections.unmodifiableList(datos); }
    /** Línea de tiempo completa (quien atiende): estados y elevaciones de prioridad. */
    public List<EventoVista> getHistorial() { return Collections.unmodifiableList(historial); }

    /** Lo que ve el alumno: solo los cambios de estado. */
    public List<EventoVista> getHistorialAlumno() {
        List<EventoVista> r = new ArrayList<>();
        for (EventoVista e : historial) {
            if (!e.isInterno()) {
                r.add(e);
            }
        }
        return r;
    }
    public List<MensajeVista> getMensajes() { return Collections.unmodifiableList(mensajes); }
    public List<AdjuntoVista> getAdjuntos() { return Collections.unmodifiableList(adjuntos); }
    /** Adjuntos que el alumno subió al registrar (no pertenecen a un mensaje). */
    public List<AdjuntoVista> getAdjuntosIniciales() {
        List<AdjuntoVista> r = new ArrayList<>();
        for (AdjuntoVista a : adjuntos) {
            if (a.getIdMensaje() == null) {
                r.add(a);
            }
        }
        return r;
    }
    /** Adjuntos de un mensaje (respuesta del alumno). */
    public List<AdjuntoVista> adjuntosDe(String idMensaje) {
        List<AdjuntoVista> r = new ArrayList<>();
        for (AdjuntoVista a : adjuntos) {
            if (idMensaje.equals(a.getIdMensaje())) {
                r.add(a);
            }
        }
        return r;
    }
    /** Códigos de estado (in-review, completed, rejected) permitidos desde el estado actual. */
    public List<String> getSiguientes() { return Collections.unmodifiableList(siguientes); }
}
