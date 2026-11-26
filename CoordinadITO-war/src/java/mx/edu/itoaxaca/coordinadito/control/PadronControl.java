package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import mx.edu.itoaxaca.coordinadito.control.vista.Descarga;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import modelo.bean.PadronService;
import modelo.bean.ResultadoCarga;
import modelo.entity.CargaPadron;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

/**
 * «control» Carga del padrón institucional (vista/padron.xhtml, HU 8.1; coordinación: sus carreras; jefatura: todas).
 * El archivo CSV se valida completo antes de escribir: con columnas faltantes no cambia nada.
 * Los alumnos del padrón entran sin capturar datos y sus cuentas pasan a «padrón institucional».
 */
@Named
@ViewScoped
public class PadronControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private PadronService padronService;

    private ResultadoCarga resultado;
    private List<CargaPadron> cargas;
    private Long vigentes;

    public void subir(FileUploadEvent evento) {
        UploadedFile f = evento.getFile();
        ResultadoCarga r = Mensajes.intentar(() -> padronService.cargar(yo(), f.getFileName(), f.getContent()));
        if (r != null) {
            resultado = r;
            cargas = null;
            vigentes = null;
            Mensajes.info("Padrón cargado", r.getCarga().getNuevos() + " nuevos, " + r.getCarga().getActualizados()
                    + " actualizados, " + r.getCarga().getRechazados() + " con errores.");
        }
    }

    public List<CargaPadron> getCargas() {
        if (cargas == null) {
            List<CargaPadron> r = Mensajes.intentar(() -> padronService.cargas(yo()));
            cargas = r == null ? Collections.<CargaPadron>emptyList() : r;
        }
        return cargas;
    }

    public long getVigentes() {
        if (vigentes == null) {
            Long r = Mensajes.intentar(() -> padronService.vigentes(yo()));
            vigentes = r == null ? 0L : r;
        }
        return vigentes;
    }

    public StreamedContent getPlantilla() {
        return Descarga.de("padron-plantilla.csv", Descarga.CSV, padronService.plantilla());
    }

    public ResultadoCarga getResultado() { return resultado; }
}
