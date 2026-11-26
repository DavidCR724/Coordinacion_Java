package mx.edu.itoaxaca.coordinadito.control.vista;

import java.time.OffsetDateTime;
import modelo.entity.CambioPrioridad;

import java.io.Serializable;
import modelo.entity.EntradaBitacora;

/**
 * Un evento de la línea de tiempo «Seguimiento» (comp-evento-historial): un cambio de estado de
 * la bitácora (HISTORIAL_SOLICITUD) o una elevación de prioridad (CAMBIO_PRIORIDAD, HU 3.3).
 * Las elevaciones son internas: solo las ve quien atiende.
 */
public class EventoVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String codigoEstado;
    private final String fecha;
    private final String actor;
    private final String comentario;
    private final String transicion;
    /** Elevación de prioridad: solo para coordinación y jefatura. */
    private final boolean interno;
    /** Para ordenar la línea de tiempo (más reciente primero). */
    private final OffsetDateTime momento;

    public EventoVista(EntradaBitacora e) {
        interno = false;
        momento = e.getFecha();
        codigoEstado = Formato.codigo(e.getEstadoNuevo());
        fecha = Formato.fechaHora(e.getFecha());
        actor = e.getActor() == null ? "Sistema" : e.getActor().getNombreCompleto();
        comentario = e.getComentario() == null ? "" : e.getComentario();
        transicion = e.getEstadoAnterior() == null ? ""
                : e.getEstadoAnterior().getEtiqueta() + " → " + e.getEstadoNuevo().getEtiqueta();
    }

    /** HU 3.3: la elevación de prioridad queda en la misma línea de tiempo que los estados. */
    public EventoVista(CambioPrioridad c) {
        interno = true;
        momento = c.getFecha();
        codigoEstado = "prioridad";
        fecha = Formato.fechaHora(c.getFecha());
        actor = c.getActor() == null ? "Sistema" : c.getActor().getNombreCompleto();
        comentario = c.getMotivo() == null ? "" : c.getMotivo();
        transicion = "Prioridad: " + c.getAnterior().getEtiqueta() + " → " + c.getNueva().getEtiqueta();
    }

    public boolean isInterno() { return interno; }
    public OffsetDateTime getMomento() { return momento; }

    public String getCodigoEstado() { return codigoEstado; }
    public String getFecha() { return fecha; }
    public String getActor() { return actor; }
    public String getComentario() { return comentario; }
    public String getTransicion() { return transicion; }
}
