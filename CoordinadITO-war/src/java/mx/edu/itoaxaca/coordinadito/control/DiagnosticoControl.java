package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.ProjectStage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import modelo.bean.DiagnosticoService;
import modelo.bean.EstadoConexion;
import modelo.bean.MigracionesService;
import modelo.bean.RevisionModelo;

/**
 * «control» Bean de la vista vista/diagnostico.xhtml.
 * Pide al servicio una sola prueba por petición y la guarda para que la vista la lea.
 * La revisión de modelos contra la base solo se hace en modo desarrollo: la página no pide
 * sesión y en producción no conviene mostrar la estructura de la base.
 */
@Named
@RequestScoped
public class DiagnosticoControl {

    @EJB
    private DiagnosticoService diagnosticoService;

    @EJB
    private MigracionesService migraciones;

    private EstadoConexion estado;
    private List<RevisionModelo> modelos;

    public EstadoConexion getEstado() {
        if (estado == null) {
            estado = diagnosticoService.probar();
        }
        return estado;
    }

    /** Lo que hizo MigracionesService al arrancar (qué aplicó, qué ya estaba y si hubo un problema). */
    public MigracionesService getMigraciones() {
        return migraciones;
    }

    public boolean isDesarrollo() {
        return FacesContext.getCurrentInstance().getApplication().getProjectStage() == ProjectStage.Development;
    }

    /** Un renglón por modelo (@Entity), ordenados por nombre. */
    public List<RevisionModelo> getModelos() {
        if (modelos == null) {
            modelos = isDesarrollo() && getEstado().isConectado()
                    ? diagnosticoService.revisarModelos() : Collections.<RevisionModelo>emptyList();
        }
        return modelos;
    }

    public long getConProblemas() {
        return getModelos().stream().filter(m -> !m.isCorrecto()).count();
    }

    public long getFilasTotales() {
        return getModelos().stream().mapToLong(m -> Math.max(0, m.getFilas())).sum();
    }

    /** Tablas de la base que ningún modelo usa (informativo: algunas se leen con SQL directo). */
    public List<String> getTablasSinModelo() {
        Set<String> usadas = new HashSet<>();
        for (RevisionModelo m : getModelos()) {
            usadas.add(m.getTabla());
            usadas.addAll(m.getRelaciones());
        }
        List<String> r = new ArrayList<>();
        for (String t : getEstado().getTablas()) {
            if (!usadas.contains(t)) {
                r.add(t);
            }
        }
        return r;
    }
}
