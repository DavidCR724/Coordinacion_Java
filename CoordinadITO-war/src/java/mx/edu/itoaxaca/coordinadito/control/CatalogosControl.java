package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mx.edu.itoaxaca.coordinadito.control.vista.Opcion;
import modelo.bean.CatalogoService;
import modelo.entity.Carrera;
import modelo.entity.Prioridad;
import modelo.entity.Rol;

/**
 * «control» Listas para los menús de selección que comparten varias pantallas
 * (#{catalogos.carreras}, #{catalogos.roles}...). Así ninguna pantalla arma su propia copia.
 * Se consulta a lo más una vez por petición.
 */
@Named("catalogos")
@RequestScoped
public class CatalogosControl {

    @EJB
    private CatalogoService catalogoService;

    private List<Opcion> carreras;

    /** valor = id de la carrera; etiqueta = «ISC · Ingeniería en Sistemas Computacionales». */
    public List<Opcion> getCarreras() {
        if (carreras == null) {
            carreras = Opcion.de(catalogoService.carreras(), Carrera::getId, c -> c.getClave() + " · " + c.getNombre());
        }
        return carreras;
    }

    public List<Opcion> getRoles() {
        return Opcion.de(Arrays.asList(Rol.values()), Rol::name, Rol::getEtiqueta);
    }

    /** Roles de personal (el de estudiante llega solo, con el primer acceso). */
    public List<Opcion> getRolesPersonal() {
        List<Opcion> r = getRoles();
        r.remove(0);
        return r;
    }

    public List<Opcion> getPrioridades() {
        return Opcion.de(Arrays.asList(Prioridad.values()), Prioridad::name, Prioridad::getEtiqueta);
    }

    /** 1 a 14 */
    public List<Integer> getSemestres() {
        List<Integer> r = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            r.add(i);
        }
        return r;
    }
}
