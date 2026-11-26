package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import mx.edu.itoaxaca.coordinadito.control.vista.AuditoriaFila;
import mx.edu.itoaxaca.coordinadito.control.vista.ListaPaginada;
import mx.edu.itoaxaca.coordinadito.control.vista.Opcion;
import modelo.bean.AuditoriaService;
import modelo.entity.Accion;
import modelo.entity.Fechas;
import modelo.entity.RegistroAuditoria;

/**
 * «control» Bitácora de auditoría (vista/auditoria.xhtml, HU 8.2; jefatura: toda; coordinación: sus carreras).
 * Solo consulta: los registros nunca se editan ni se borran. Filtros por acción, fechas y texto
 * (correo, nombre o detalle), paginada en la base.
 */
@Named
@ViewScoped
public class AuditoriaControl extends ControlBase {

    private static final long serialVersionUID = 1L;
    private static final int DIAS_POR_OMISION = 30;

    @EJB
    private AuditoriaService auditoriaService;

    private String accion;
    private LocalDate desde = Fechas.hoy().minusDays(DIAS_POR_OMISION);
    private LocalDate hasta = Fechas.hoy();
    private String texto;

    private final ListaPaginada<RegistroAuditoria, AuditoriaFila> registros = new ListaPaginada<>(
            (numero, tamano) -> auditoriaService.consultar(yo(), accion == null || accion.isEmpty() ? null : Accion.valueOf(accion),
                    desde, hasta, texto, numero, tamano),
            AuditoriaFila::new, AuditoriaFila::getId);

    public List<Opcion> getAcciones() {
        return Opcion.de(Arrays.asList(Accion.values()), Accion::name, Accion::getEtiqueta);
    }

    public ListaPaginada<RegistroAuditoria, AuditoriaFila> getRegistros() { return registros; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }
    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta = hasta; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
}
