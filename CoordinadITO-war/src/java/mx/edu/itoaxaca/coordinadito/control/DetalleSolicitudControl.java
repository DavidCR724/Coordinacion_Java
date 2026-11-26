package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mx.edu.itoaxaca.coordinadito.control.vista.AdjuntoVista;
import mx.edu.itoaxaca.coordinadito.control.vista.Descarga;
import mx.edu.itoaxaca.coordinadito.control.vista.Formato;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.SolicitudDetalle;
import modelo.bean.AccesoDenegadoException;
import modelo.bean.ArchivoNuevo;
import modelo.bean.NoEncontradoException;
import modelo.bean.SolicitudService;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Prioridad;
import modelo.entity.Solicitud;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

/**
 * «control» Detalle y atención de una solicitud (vista/solicitud-detalle.xhtml).
 *
 *     Todos        → datos, adjuntos, mensajes y bitácora (HU 2.3, 2.8).
 *     Coordinación → cambiar estado con constancia (HU 2.4, 2.7), pedir información (HU 2.5),
 *                    elevar prioridad (HU 3.3).
 *     Estudiante   → responder la petición de información (HU 2.6).
 *
 * Cada acción vuelve a leer la solicitud de la base para mostrar el estado real (y la bitácora nueva).
 */
@Named
@ViewScoped
public class DetalleSolicitudControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private SolicitudService solicitudService;

    /** ?folio= (solo el número). */
    private String folio;
    private SolicitudDetalle sol;
    private boolean visible;

    // Panel «Atender»
    private String siguienteEstado;
    private String constancia;
    /** Valor de la opción «Pedir información» en el panel «Atender» (es el código del estado ESPERA). */
    public static final String PEDIR = "waiting";
    // Panel «Responder» (alumno)
    private String respuesta;
    private final List<ArchivoNuevo> archivosRespuesta = new ArrayList<>();
    // Panel «Prioridad»
    private String nuevaPrioridad;
    private String motivoPrioridad;

    /** f:viewAction */
    public void cargar() {
        if (sol == null) {
            recargar();
        }
    }

    /** Vuelve a leer la solicitud (también lo usa CitaControl tras pedir o agendar una cita). */
    public void recargar() {
        visible = false;
        Integer numero = numeroDeFolio(folio);
        if (numero == null) {
            return;
        }
        try {
            sol = new SolicitudDetalle(solicitudService.detalle(numero, yo()));
            visible = true;
            // Queda elegida la opción más común (avanzar); la coordinación solo cambia si hace falta.
            List<String> acciones = getAcciones();
            if (siguienteEstado == null || !acciones.contains(siguienteEstado)) {
                siguienteEstado = acciones.isEmpty() ? null : acciones.get(0);
            }
        } catch (AccesoDenegadoException | NoEncontradoException e) {
            sol = null;
        }
    }

    private int numero() {
        return Integer.parseInt(sol.getFolio());
    }

    // ---------------- Coordinación ----------------

    /**
     * Un solo botón para atender: cambia el estado o, si eligió «Pedir información», manda la
     * petición (el texto es la petición). El comentario solo es obligatorio al rechazar o al pedir
     * información; eso lo decide el servicio.
     */
    public void registrarCambio() {
        if (PEDIR.equals(siguienteEstado)) {
            if (Mensajes.intentar(() -> solicitudService.solicitarInformacion(numero(), constancia, yo())) != null) {
                constancia = null;
                siguienteEstado = null;
                recargar();
                Mensajes.info("Petición enviada", "El alumno recibió tu mensaje. La solicitud espera su respuesta.");
            }
            return;
        }
        EstadoSolicitud nuevo = Formato.estado(siguienteEstado);
        if (Mensajes.intentar(() -> solicitudService.cambiarEstado(numero(), nuevo, constancia, yo())) != null) {
            constancia = null;
            recargar();
            Mensajes.info("Cambio registrado", "La solicitud quedó como «" + sol.getEtiquetaEstado() + "» y se avisó al alumno.");
        }
    }

    /** Opciones del panel «Atender»: los estados permitidos y, si no está esperando ya, pedir información. */
    public List<String> getAcciones() {
        List<String> r = new ArrayList<>();
        if (sol != null) {
            r.addAll(sol.getSiguientes());
            if (!sol.isEnEspera()) {
                r.add(PEDIR);
            }
        }
        return r;
    }

    /** ¿El texto es obligatorio con la acción elegida? */
    public boolean isTextoObligatorio() {
        return PEDIR.equals(siguienteEstado) || "rejected".equals(siguienteEstado);
    }

    public void elevarPrioridad() {
        Prioridad p = nuevaPrioridad == null || nuevaPrioridad.isEmpty() ? null : Prioridad.valueOf(nuevaPrioridad);
        if (Mensajes.intentar(() -> solicitudService.elevarPrioridad(numero(), p, motivoPrioridad, yo())) != null) {
            nuevaPrioridad = null;
            motivoPrioridad = null;
            recargar();
            PrimeFaces.current().ajax().update("detalle");   // el botón solo actualiza su formulario
            Mensajes.info("Prioridad actualizada", "La solicitud ahora es de prioridad " + p.getEtiqueta().toLowerCase() + ".");
        }
    }

    /** Prioridades mayores que la actual (la prioridad nunca baja). */
    public List<String> getPrioridadesMayores() {
        List<String> r = new ArrayList<>();
        if (sol != null) {
            Prioridad actual = Prioridad.valueOf(sol.getPrioridad());
            for (Prioridad p : Prioridad.values()) {
                if (p.esMayorQue(actual)) {
                    r.add(p.name());
                }
            }
        }
        return r;
    }

    // ---------------- Alumno ----------------

    public void subirRespuesta(FileUploadEvent evento) {
        UploadedFile f = evento.getFile();
        if (archivosRespuesta.size() >= 5) {
            Mensajes.error("Puedes adjuntar máximo 5 archivos.");
            return;
        }
        archivosRespuesta.add(new ArchivoNuevo(f.getFileName(), f.getContentType(), f.getContent()));
    }

    public void quitarArchivo(int indice) {
        if (indice >= 0 && indice < archivosRespuesta.size()) {
            archivosRespuesta.remove(indice);
        }
    }

    public void responder() {
        Solicitud s = Mensajes.intentar(() -> solicitudService.responder(yo(), numero(), respuesta, archivosRespuesta));
        if (s != null) {
            respuesta = null;
            archivosRespuesta.clear();
            recargar();
            Mensajes.info("Respuesta enviada", "Coordinación ya puede continuar con tu solicitud.");
        }
    }

    public String tamano(ArchivoNuevo a) {
        return Formato.tamano(a.getTamano());
    }

    // ---------------- Todos ----------------

    /** p:fileDownload de un adjunto (mismos permisos que el detalle). */
    public StreamedContent descargar(String idAdjunto) {
        AdjuntoVista adjunto = null;
        for (AdjuntoVista a : sol.getAdjuntos()) {
            if (a.getId().equals(idAdjunto)) {
                adjunto = a;
            }
        }
        if (adjunto == null) {
            return null;
        }
        return Descarga.de(adjunto.getNombre(), adjunto.getMime(),
                Mensajes.intentar(() -> solicitudService.descargarAdjunto(numero(), UUID.fromString(idAdjunto), yo())));
    }

    // ---------------- Qué puede hacer quien mira ----------------

    public boolean isPuedeAtender() {
        return visible && esRol("coordinator") && !sol.isCerrada();
    }

    public boolean isPuedeResponder() {
        return visible && esRol("student") && sol.isEnEspera();
    }

    public boolean isPuedeElevar() {
        return isPuedeAtender() && !getPrioridadesMayores().isEmpty();
    }

    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }
    public SolicitudDetalle getSol() { return sol; }
    public boolean isVisible() { return visible; }
    public String getSiguienteEstado() { return siguienteEstado; }
    public void setSiguienteEstado(String siguienteEstado) { this.siguienteEstado = siguienteEstado; }
    public String getConstancia() { return constancia; }
    public void setConstancia(String constancia) { this.constancia = constancia; }
    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
    public List<ArchivoNuevo> getArchivosRespuesta() { return archivosRespuesta; }
    public String getNuevaPrioridad() { return nuevaPrioridad; }
    public void setNuevaPrioridad(String nuevaPrioridad) { this.nuevaPrioridad = nuevaPrioridad; }
    public String getMotivoPrioridad() { return motivoPrioridad; }
    public void setMotivoPrioridad(String motivoPrioridad) { this.motivoPrioridad = motivoPrioridad; }
}
