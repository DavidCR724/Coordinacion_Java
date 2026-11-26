package mx.edu.itoaxaca.coordinadito.control.vista;

import modelo.bean.Seguimiento;

/** Una solicitud abierta que ya rebasó el tiempo esperado de su trámite (HU 7.3). */
public class RezagoFila extends SolicitudFila {

    private static final long serialVersionUID = 1L;

    private final long dias;
    private final long exceso;
    private final int esperados;

    public RezagoFila(Seguimiento.Rezago r) {
        super(r.getSolicitud());
        dias = r.getDias();
        exceso = r.getExceso();
        esperados = r.getSolicitud().getTramite().getDiasEsperados();
    }

    public long getDias() { return dias; }
    public long getExceso() { return exceso; }
    public int getEsperados() { return esperados; }
    /** Más del doble del tiempo esperado: se marca en rojo. */
    public boolean isGrave() { return dias >= 2L * esperados; }
}
