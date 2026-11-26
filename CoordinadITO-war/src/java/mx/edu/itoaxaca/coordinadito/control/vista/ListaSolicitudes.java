package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Objeto de vista: una lista de solicitudes partida en dos vistas, «Activas» (recibidas, en
 * revisión o esperando al alumno) y «Finalizadas» (finalizadas o rechazadas: el historial).
 * Sustituye al filtro por estado de la columna: dos opciones en lugar de seis.
 * Las dos listas son instancias fijas: p:dataTable las ordena «en su lugar» y el orden se conserva.
 * Lo usan SolicitudesControl (estudiante, coordinación y jefatura) y el inicio de coordinación.
 */
public class ListaSolicitudes implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ACTIVAS = "activas";
    public static final String FINALIZADAS = "finalizadas";

    private final List<SolicitudFila> activas = new ArrayList<>();
    private final List<SolicitudFila> finalizadas = new ArrayList<>();
    private String vista = ACTIVAS;

    public ListaSolicitudes(Collection<SolicitudFila> filas) {
        for (SolicitudFila f : filas) {
            (f.isActiva() ? activas : finalizadas).add(f);
        }
    }

    /** Lo que se dibuja según la vista elegida. */
    public List<SolicitudFila> getVisibles() {
        return isFinalizadas() ? finalizadas : activas;
    }

    public boolean isFinalizadas() { return FINALIZADAS.equals(vista); }
    public String getVista() { return vista; }
    public void setVista(String vista) { this.vista = FINALIZADAS.equals(vista) ? FINALIZADAS : ACTIVAS; }
    public int getNumeroActivas() { return activas.size(); }
    public int getNumeroFinalizadas() { return finalizadas.size(); }
    public boolean isVacia() { return activas.isEmpty() && finalizadas.isEmpty(); }
}
