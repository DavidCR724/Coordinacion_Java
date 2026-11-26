package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.edu.itoaxaca.coordinadito.control.vista.CampoVista;
import mx.edu.itoaxaca.coordinadito.control.vista.Formato;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.TramiteVista;
import modelo.bean.ArchivoNuevo;
import modelo.bean.NoEncontradoException;
import modelo.bean.SolicitudService;
import modelo.bean.TramiteService;
import modelo.entity.Solicitud;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

/**
 * «control» Formulario de una solicitud nueva (vista/tramite-detalle.xhtml, HU 1.2, 1.5, 2.2, 2.9).
 *
 * Vive lo que vive la vista (@ViewScoped): los valores capturados y los archivos subidos se
 * conservan entre el paso «Datos» y el paso «Revisión» del p:wizard. Nada se guarda en la base
 * hasta «Confirmar solicitud»; ahí SolicitudService vuelve a validar todo.
 */
@Named
@ViewScoped
public class NuevaSolicitudControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private TramiteService tramiteService;

    @EJB
    private SolicitudService solicitudService;

    /** ?id= del trámite (UUID). */
    private String id;
    private TramiteVista tramite;
    private final Map<String, String> respuestas = new HashMap<>();
    private final List<ArchivoNuevo> archivos = new ArrayList<>();
    private String folio;
    private String codigoEstado;
    /** Folio (número) de la solicitud de este trámite que el alumno ya tiene abierta; 0 = ninguna. */
    private Integer folioAbierto;

    /** f:viewAction: carga el trámite con su formulario. */
    public void cargar() {
        if (tramite != null || id == null) {
            return;
        }
        try {
            tramite = new TramiteVista(tramiteService.formulario(UUID.fromString(id)), true);
        } catch (IllegalArgumentException | NoEncontradoException e) {
            tramite = null;
        }
    }

    /** p:fileUpload (modo avanzado): cada archivo llega en su propia petición. */
    public void subir(FileUploadEvent evento) {
        UploadedFile f = evento.getFile();
        if (archivos.size() >= 5) {
            Mensajes.error("Puedes adjuntar máximo 5 archivos.");
            return;
        }
        archivos.add(new ArchivoNuevo(f.getFileName(), f.getContentType(), f.getContent()));
    }

    public void quitar(int indice) {
        if (indice >= 0 && indice < archivos.size()) {
            archivos.remove(indice);
        }
    }

    /** «Confirmar solicitud». */
    public void registrar() {
        Map<UUID, String> valores = new HashMap<>();
        for (CampoVista c : tramite.getCampos()) {
            String v = respuestas.get(c.getNombre());
            if (v != null && !v.trim().isEmpty()) {
                valores.put(UUID.fromString(c.getNombre()), v.trim());
            }
        }
        Solicitud s = Mensajes.intentar(() -> solicitudService.registrar(yo(),
                UUID.fromString(tramite.getId()), null, valores, archivos));
        if (s != null) {
            folio = s.getFolioTexto();
            codigoEstado = Formato.codigo(s.getEstado());
            respuestas.clear();
            archivos.clear();
        }
    }

    /** Texto para el paso de revisión (en SELECCION y FECHA, tal cual se capturó). */
    public String valor(String nombreCampo) {
        String v = respuestas.get(nombreCampo);
        return v == null || v.trim().isEmpty() ? "—" : v;
    }

    public String tamano(ArchivoNuevo a) {
        return Formato.tamano(a.getTamano());
    }

    public boolean isRegistrada() { return folio != null; }

    /** HU 2.9: si ya tiene una abierta de este trámite, se le dice antes de llenar el formulario. */
    public Integer getFolioAbierto() {
        if (folioAbierto == null) {
            Integer f = tramite == null ? null
                    : Mensajes.intentar(() -> solicitudService.folioAbierto(yo(), UUID.fromString(tramite.getId())));
            folioAbierto = f == null ? 0 : f;
        }
        return folioAbierto == 0 ? null : folioAbierto;
    }

    public String getFolioAbiertoTexto() {
        Integer f = getFolioAbierto();
        return f == null ? null : String.format("ITO-%04d", f);
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public TramiteVista getTramite() { return tramite; }
    public Map<String, String> getRespuestas() { return respuestas; }
    public List<ArchivoNuevo> getArchivos() { return archivos; }
    public String getFolio() { return folio; }
    public String getCodigoEstado() { return codigoEstado; }
}
