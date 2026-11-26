package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.util.UUID;
import modelo.bean.GrupoResumen;
import modelo.entity.Materia;
import modelo.entity.Solicitud;

/**
 * Una solicitud de apertura de grupo como la pintan comp-tarjeta-grupo, la tabla de grupos y el
 * detalle (HU 4.2 – 4.4). Mantiene los nombres de propiedad de la demostración.
 */
public class GrupoVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String folioTexto;
    private final String idMateria;
    private final String materia;
    private final String clave;
    private final int semestre;
    private final String claveCarrera;
    private final String carrera;
    private final long adhesiones;
    private final String codigoEstado;
    private final String actualizacion;
    private final String registroIso;
    private final boolean propia;
    private final boolean adherido;

    public GrupoVista(Solicitud s, long interesados, boolean propia, boolean adherido) {
        Materia m = s.getMateria();
        id = String.valueOf(s.getFolio());
        folioTexto = s.getFolioTexto();
        idMateria = m.getId().toString();
        materia = m.getNombre();
        clave = m.getClave();
        semestre = m.getSemestre() == null ? 0 : m.getSemestre();
        claveCarrera = m.getCarrera().getClave();
        carrera = m.getCarrera().getNombre();
        adhesiones = interesados;
        codigoEstado = Formato.codigo(s.getEstado());
        actualizacion = Formato.fecha(s.getFechaActualizacion());
        registroIso = Formato.iso(s.getFechaRegistro());
        this.propia = propia;
        // En las vistas «adherido» significa «estoy en la lista», también si la inicié yo.
        this.adherido = propia || adherido;
    }

    public static GrupoVista de(GrupoResumen r) {
        return new GrupoVista(r.getSolicitud(), r.getInteresados(), r.isPropia(), r.isAdherido());
    }

    /** Para el detalle: se calcula si quien mira la inició o está en la lista. */
    public static GrupoVista de(Solicitud s, UUID quienMira) {
        boolean propia = s.getSolicitante().getId().equals(quienMira);
        boolean adherido = s.getAdhesiones().stream().anyMatch(a -> a.getAlumno().getId().equals(quienMira));
        return new GrupoVista(s, s.getNumeroInteresados(), propia, adherido);
    }

    public boolean isActiva() {
        return "received".equals(codigoEstado) || "in-review".equals(codigoEstado) || "waiting".equals(codigoEstado);
    }

    public String getId() { return id; }
    public String getFolioTexto() { return folioTexto; }
    public String getIdMateria() { return idMateria; }
    public String getMateria() { return materia; }
    public String getClave() { return clave; }
    public int getSemestre() { return semestre; }
    public String getClaveCarrera() { return claveCarrera; }
    public String getCarrera() { return carrera; }
    public long getAdhesiones() { return adhesiones; }
    public String getCodigoEstado() { return codigoEstado; }
    public String getActualizacion() { return actualizacion; }
    public String getRegistroIso() { return registroIso; }
    public boolean isPropia() { return propia; }
    public boolean isAdherido() { return adherido; }
}
