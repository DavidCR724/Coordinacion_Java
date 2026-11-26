package mx.edu.itoaxaca.coordinadito.control.vista;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import modelo.entity.RegistroAuditoria;

/** Un renglón de la bitácora de auditoría (HU 8.2), con el detalle JSON en pares legibles. */
public class AuditoriaFila implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String fecha;
    private final String actor;
    private final String correo;
    private final String accion;
    private final String etiquetaAccion;
    private final String entidad;
    private final String ip;
    private final List<DatoVista> detalle;

    public AuditoriaFila(RegistroAuditoria r) {
        id = String.valueOf(r.getId());
        fecha = Formato.fechaHora(r.getFecha());
        actor = r.getActor() == null ? "Sin cuenta" : r.getActor().getNombreCompleto();
        correo = r.getActor() == null ? "" : r.getActor().getCorreo();
        accion = r.getAccion().name();
        etiquetaAccion = r.getAccion().getEtiqueta();
        entidad = r.getEntidad() + (r.getIdEntidad() == null ? "" : " · " + r.getIdEntidad());
        ip = r.getIp() == null ? "" : r.getIp();
        detalle = pares(r.getDetalle());
    }

    /** {"antes":"COORDINADOR [ISC]","despues":"JEFE []"} → antes: …, despues: … */
    private static List<DatoVista> pares(String json) {
        List<DatoVista> r = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return r;
        }
        try (JsonReader lector = Json.createReader(new StringReader(json))) {
            JsonObject o = lector.readObject();
            for (Map.Entry<String, JsonValue> e : o.entrySet()) {
                JsonValue v = e.getValue();
                r.add(new DatoVista(e.getKey(), v instanceof JsonString ? ((JsonString) v).getString() : v.toString(), false));
            }
        } catch (RuntimeException e) {
            r.add(new DatoVista("detalle", json, true));
        }
        return r;
    }

    public String getId() { return id; }
    public String getFecha() { return fecha; }
    public String getActor() { return actor; }
    public String getCorreo() { return correo; }
    public String getAccion() { return accion; }
    public String getEtiquetaAccion() { return etiquetaAccion; }
    public String getEntidad() { return entidad; }
    public String getIp() { return ip; }
    public List<DatoVista> getDetalle() { return detalle; }
}
