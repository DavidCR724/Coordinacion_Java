package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.edu.itoaxaca.coordinadito.control.vista.Descarga;
import mx.edu.itoaxaca.coordinadito.control.vista.GraficaBarras;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.Periodo;
import mx.edu.itoaxaca.coordinadito.control.vista.RezagoFila;
import modelo.bean.Seguimiento;
import modelo.bean.SeguimientoService;
import org.primefaces.model.StreamedContent;

import static mx.edu.itoaxaca.coordinadito.control.vista.GraficaBarras.*;

/**
 * «control» Paneles de seguimiento de jefatura (HU 7.1 – 7.4): volumen, tiempos de atención,
 * rezagos y exportación del historial. Las tres pantallas comparten el periodo elegido y cada dato
 * se pide a la base solo la primera vez que una pantalla lo lee (y otra vez al cambiar el filtro).
 */
@Named
@ViewScoped
public class SeguimientoControl extends ControlBase {

    private static final long serialVersionUID = 1L;
    private static final int PERIODOS = 6;

    @EJB
    private SeguimientoService seguimientoService;

    private String periodo = Periodo.actual().getId();
    /** Rezagos: carrera elegida (vacío = todas). */
    private String carrera;

    private Seguimiento.Volumen volumen;
    private List<Seguimiento.Tiempo> tiempos;
    private List<RezagoFila> rezagos;

    /** Al cambiar el periodo o la carrera: lo ya calculado deja de valer. */
    public void cambiarFiltro() {
        volumen = null;
        tiempos = null;
        rezagos = null;
    }

    // ---------------- HU 7.1 Volumen ----------------

    public Seguimiento.Volumen getVolumen() {
        if (volumen == null) {
            Periodo p = elegido();
            volumen = Mensajes.intentar(() -> seguimientoService.volumen(yo(), p.getDesde(), p.getHasta()));
        }
        return volumen;
    }

    public String getGraficaTramites() {
        return GraficaBarras.de(getVolumen().getPorTramite(), Seguimiento.Fila::getNombre)
                .serie("Recibidas", Seguimiento.Fila::getRecibidas, AZUL)
                .serie("En revisión", Seguimiento.Fila::getEnRevision, NARANJA)
                .serie("Requieren información", Seguimiento.Fila::getEnEspera, AMARILLO)
                .serie("Finalizadas", Seguimiento.Fila::getFinalizadas, VERDE)
                .serie("Rechazadas", Seguimiento.Fila::getRechazadas, ROJO)
                .horizontal().apilada().json();
    }

    public String getGraficaCarreras() {
        return GraficaBarras.de(getVolumen().getPorCarrera(), Seguimiento.Fila::getNombre)
                .serie("Solicitudes", Seguimiento.Fila::getTotal, MARINO)
                .json();
    }

    /** 0–100 */
    public long porcentaje(long parte) {
        long total = getVolumen().getTotal().getTotal();
        return total == 0 ? 0 : Math.round(100.0 * parte / total);
    }

    // ---------------- HU 7.2 Tiempos ----------------

    public List<Seguimiento.Tiempo> getTiempos() {
        if (tiempos == null) {
            Periodo p = elegido();
            List<Seguimiento.Tiempo> r = Mensajes.intentar(() -> seguimientoService.tiempos(yo(), p.getDesde(), p.getHasta()));
            tiempos = r == null ? Collections.<Seguimiento.Tiempo>emptyList() : r;
        }
        return tiempos;
    }

    /** Promedio contra lo esperado, en días (redondeado). */
    public String getGraficaTiempos() {
        return GraficaBarras.de(getTiempos(), Seguimiento.Tiempo::getTramite)
                .serie("Promedio real (días)", t -> Math.round(t.getPromedioDias()), AZUL)
                .serie("Esperado (días)", Seguimiento.Tiempo::getDiasEsperados, NARANJA)
                .horizontal().json();
    }

    public int getResueltas() {
        return getTiempos().stream().mapToInt(Seguimiento.Tiempo::getResueltas).sum();
    }

    public int getPorcentajeEnTiempo() {
        int resueltas = getResueltas();
        int dentro = getTiempos().stream().mapToInt(Seguimiento.Tiempo::getDentroDeTiempo).sum();
        return resueltas == 0 ? 0 : Math.round(100f * dentro / resueltas);
    }

    // ---------------- HU 7.3 Rezagos ----------------

    public List<RezagoFila> getRezagos() {
        if (rezagos == null) {
            UUID c = carrera == null || carrera.isEmpty() ? null : UUID.fromString(carrera);
            List<Seguimiento.Rezago> r = Mensajes.intentar(() -> seguimientoService.rezagos(yo(), c));
            rezagos = r == null ? Collections.<RezagoFila>emptyList()
                    : r.stream().map(RezagoFila::new).collect(Collectors.toList());
        }
        return rezagos;
    }

    public long getRezagosGraves() {
        return getRezagos().stream().filter(RezagoFila::isGrave).count();
    }

    // ---------------- HU 7.4 Exportar ----------------

    /** p:fileDownload: historial del periodo en CSV (se abre en Excel). Queda en la auditoría. */
    public StreamedContent getExportacion() {
        Periodo p = elegido();
        return Descarga.de("historial-" + p.getId() + ".csv", Descarga.CSV,
                Mensajes.intentar(() -> seguimientoService.exportar(yo(), p.getDesde(), p.getHasta())));
    }

    // ---------------------------------------------------------------------

    public List<Periodo> getPeriodos() { return Periodo.recientes(PERIODOS); }
    public Periodo elegido() { return Periodo.de(periodo); }
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public String getCarrera() { return carrera; }
    public void setCarrera(String carrera) { this.carrera = carrera; }
}
