package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.PersonaFila;
import modelo.bean.CuentaService;
import modelo.entity.Rol;
import mx.edu.itoaxaca.coordinadito.control.vista.Opcion;
import modelo.entity.Carrera;
import modelo.entity.Usuario;
import org.primefaces.PrimeFaces;

/**
 * «control» Roles y carreras del personal (vista/roles.xhtml, HU 6.3).
 * Jefatura: todo el personal y cualquier rol. Coordinación: las coordinaciones de sus carreras,
 * y solo puede otorgar el rol de coordinación con sus propias carreras (lo decide CuentaService;
 * aquí solo se le ofrecen esas opciones).
 * El rol se otorga de forma explícita a una cuenta institucional, nunca se deduce del correo.
 * Un mismo diálogo sirve para dar de alta (sin cuenta elegida) y para cambiar el rol.
 * Todo queda en la auditoría (lo escribe CuentaService).
 */
@Named
@ViewScoped
public class RolesControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB
    private CuentaService cuentaService;

    private List<PersonaFila> cuentas;
    private List<Opcion> rolesAsignables;
    private List<Opcion> carrerasAsignables;

    // Diálogo
    private PersonaFila seleccionada;
    private String correo;
    private String nombres;
    private String paterno;
    private String materno;
    private String rolElegido;
    private List<String> carreras = new ArrayList<>();

    /** Roles que puede otorgar quien mira (para el diálogo). */
    public List<Opcion> getRolesAsignables() {
        if (rolesAsignables == null) {
            List<Rol> r = Mensajes.intentar(() -> cuentaService.rolesQuePuedeAsignar(yo()));
            rolesAsignables = Opcion.de(r == null ? Collections.<Rol>emptyList() : r, Rol::name, Rol::getEtiqueta);
        }
        return rolesAsignables;
    }

    /** Carreras que puede asignar quien mira: jefatura, todas; coordinación, las suyas. */
    public List<Opcion> getCarrerasAsignables() {
        if (carrerasAsignables == null) {
            List<Carrera> r = Mensajes.intentar(() -> cuentaService.carrerasQuePuedeAsignar(yo()));
            carrerasAsignables = Opcion.de(r == null ? Collections.<Carrera>emptyList() : r, Carrera::getId,
                    c -> c.getClave() + " · " + c.getNombre());
        }
        return carrerasAsignables;
    }

    public List<PersonaFila> getCuentas() {
        if (cuentas == null) {
            List<Usuario> r = Mensajes.intentar(() -> cuentaService.personal(yo()));
            cuentas = r == null ? Collections.<PersonaFila>emptyList()
                    : r.stream().map(PersonaFila::cuenta).collect(Collectors.toList());
        }
        return cuentas;
    }

    /** «Asignar rol»: diálogo vacío para una cuenta nueva. */
    public void nueva() {
        abrir(null);
    }

    /** Lápiz de una fila: diálogo con el rol y las carreras actuales. */
    public void editar(PersonaFila cuenta) {
        abrir(cuenta);
    }

    public void guardar() {
        Rol rol = rolElegido == null ? null : Rol.valueOf(rolElegido);
        Set<UUID> ids = carreras.stream().map(UUID::fromString).collect(Collectors.toCollection(HashSet::new));
        boolean ok = seleccionada == null
                ? hecho(() -> cuentaService.crearPersonal(yo(), correo, nombres, paterno, materno, rol, ids),
                        "Cuenta dada de alta", correo + " ya puede entrar con su cuenta institucional.")
                : hecho(() -> cuentaService.cambiarRol(yo(), UUID.fromString(seleccionada.getId()), rol, ids),
                        "Rol actualizado", seleccionada.getNombre() + " ahora es " + rol.getEtiqueta().toLowerCase() + ".");
        if (ok) {
            cuentas = null;
            PrimeFaces.current().executeScript("PF('dlgRol').hide()");
        }
    }

    /** Dar de baja o reactivar (sin borrar su historial). */
    public void alternarActiva(PersonaFila cuenta) {
        boolean activar = !cuenta.isActivo();
        if (hecho(() -> cuentaService.definirActiva(yo(), UUID.fromString(cuenta.getId()), activar),
                activar ? "Cuenta reactivada" : "Cuenta desactivada",
                cuenta.getCorreo() + (activar ? " puede volver a entrar." : " ya no puede entrar; su historial se conserva."))) {
            cuentas = null;
        }
    }

    private void abrir(PersonaFila cuenta) {
        seleccionada = cuenta;
        correo = cuenta == null ? null : cuenta.getCorreo();
        nombres = null;
        paterno = null;
        materno = null;
        rolElegido = cuenta == null ? Rol.COORDINADOR.name() : cuenta.getRol();
        carreras = cuenta == null ? new ArrayList<String>() : new ArrayList<>(cuenta.getIdsCarreras());
        // Una coordinación con una sola carrera no tiene que elegirla.
        if (cuenta == null && getCarrerasAsignables().size() == 1) {
            carreras.add(getCarrerasAsignables().get(0).getValor());
        }
    }

    public boolean isNueva() { return seleccionada == null; }
    public PersonaFila getSeleccionada() { return seleccionada; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getPaterno() { return paterno; }
    public void setPaterno(String paterno) { this.paterno = paterno; }
    public String getMaterno() { return materno; }
    public void setMaterno(String materno) { this.materno = materno; }
    public String getRolElegido() { return rolElegido; }
    public void setRolElegido(String rolElegido) { this.rolElegido = rolElegido; }
    public List<String> getCarreras() { return carreras; }
    public void setCarreras(List<String> carreras) { this.carreras = carreras == null ? new ArrayList<String>() : carreras; }
}
