package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.ProjectStage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import mx.edu.itoaxaca.coordinadito.control.acceso.ConfiguracionOidc;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.UsuarioVista;
import modelo.bean.AccesoService;
import modelo.bean.CodigoAccesoService;
import modelo.bean.Identidad;
import modelo.bean.NotificacionService;
import modelo.bean.ResultadoAcceso;

/**
 * «control» Sesión del usuario: quién es, qué rol tiene y si le falta completar su primer acceso.
 *
 * Hay tres maneras de llegar aquí y todas terminan en {@link #abrir}:
 *   - Cuenta institucional (OIDC): AccesoServlet verifica el token y entrega la Identidad.
 *   - Código por correo: la persona escribe su correo, recibe un código de 6 dígitos y lo escribe
 *     (CodigoAccesoService). Aparece cuando Payara tiene la sesión de correo mail/coordinadito.
 *   - Acceso de desarrollo: solo con PROJECT_STAGE = Development; basta el correo. Sirve para
 *     probar sin el proveedor institucional y desaparece en producción.
 * Los permisos NO se deciden con esta copia: cada servicio recarga al usuario de la base.
 */
@Named
@SessionScoped
public class SesionControl implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String CLAVE_SIN_LEER = "coordinadito.sinLeer";

    @EJB private AccesoService accesoService;
    @EJB private NotificacionService notificacionService;
    @EJB private CodigoAccesoService codigoAccesoService;
    @Inject private ConfiguracionOidc oidc;

    private UsuarioVista usuario;
    /** Estudiante autenticado que aún no declara carrera y semestre (HU 6.4). */
    private Identidad pendiente;
    /** Formulario de acceso de desarrollo. */
    private String correo;
    /** Acceso con código: correo al que se mandó, el código que escribe y en qué paso va. */
    private String correoCodigo;
    private String codigo;
    private boolean codigoEnviado;
    /** Motivo por el que el acceso institucional regresó al login; se muestra una vez. */
    private String aviso;

    /**
     * Abre la sesión con el resultado del acceso y devuelve la página a la que hay que ir.
     * Cambia el id de sesión para evitar la fijación de sesión.
     */
    public String abrir(ResultadoAcceso r, HttpServletRequest peticion) {
        peticion.changeSessionId();
        if (r.isPrimerAcceso()) {
            usuario = null;
            pendiente = r.getIdentidad();
            return "/vista/primer-acceso.xhtml";
        }
        pendiente = null;
        usuario = new UsuarioVista(r.getUsuario());
        return "/vista/inicio.xhtml";
    }

    /** Después de cambiar datos de la cuenta (primer acceso): vuelve a leerla. */
    public void refrescar() {
        if (usuario != null) {
            usuario = new UsuarioVista(accesoService.cuenta(usuario.getId()));
        }
    }

    /** Termina el primer acceso: ya hay cuenta. */
    public void completarPrimerAcceso(modelo.entity.Usuario nuevo) {
        pendiente = null;
        usuario = new UsuarioVista(nuevo);
    }

    // ---------------------------------------------------------------------
    // Acceso con código por correo
    // ---------------------------------------------------------------------

    /** Paso 1: pide el código. La respuesta no dice si el correo tiene cuenta (ver CodigoAccesoService). */
    public void pedirCodigo() {
        if (Mensajes.intentar(() -> codigoAccesoService.solicitar(correoCodigo))) {
            correoCodigo = correoCodigo.trim().toLowerCase(java.util.Locale.ROOT);
            codigoEnviado = true;
            codigo = null;
            Mensajes.info("Revisa tu correo",
                    "Si " + correoCodigo + " puede entrar a CoordinadITO, te enviamos un código de 6 dígitos. Puede tardar un minuto; revisa también la carpeta de spam.");
        }
    }

    /** Paso 2: entra con el código. */
    public String entrarConCodigo() {
        ResultadoAcceso r = Mensajes.intentar(() -> accesoService.ingresarConCodigo(correoCodigo, codigo));
        codigo = null;
        if (r == null) {
            return null;
        }
        codigoEnviado = false;
        correoCodigo = null;
        return Mensajes.redirigir(abrir(r, (HttpServletRequest) externo().getRequest()).replace(".xhtml", ""));
    }

    /** Regresa al paso 1 (otro correo). */
    public void cambiarCorreo() {
        codigoEnviado = false;
        codigo = null;
    }

    /** ¿Hay sesión de correo para ofrecer el acceso con código? */
    public boolean isAccesoPorCodigo() {
        Map<Object, Object> atributos = FacesContext.getCurrentInstance().getAttributes();
        Boolean hay = (Boolean) atributos.get("coordinadito.accesoPorCodigo");
        if (hay == null) {
            hay = codigoAccesoService.disponible();
            atributos.put("coordinadito.accesoPorCodigo", hay);
        }
        return hay;
    }

    public String getCorreoCodigo() { return correoCodigo; }
    public void setCorreoCodigo(String correoCodigo) { this.correoCodigo = correoCodigo; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public boolean isCodigoEnviado() { return codigoEnviado; }

    // ---------------------------------------------------------------------
    // Acceso de desarrollo (solo PROJECT_STAGE = Development)
    // ---------------------------------------------------------------------

    public String entrar() {
        if (!isModoDesarrollo()) {
            Mensajes.error("El acceso con solo el correo está deshabilitado. Usa tu cuenta institucional.");
            return null;
        }
        ResultadoAcceso r = Mensajes.intentar(() -> accesoService.ingresar(new Identidad(correo, null, null)));
        if (r == null) {
            return null;
        }
        correo = null;
        return Mensajes.redirigir(abrir(r, (HttpServletRequest) externo().getRequest()).replace(".xhtml", ""));
    }

    public String entrarComo(String cuenta) {
        correo = cuenta;
        return entrar();
    }

    /** «Salir de mi espacio». */
    public void salir() throws IOException {
        ExternalContext ec = externo();
        ec.invalidateSession();
        ec.redirect(ec.getRequestContextPath() + "/vista/login.xhtml");
    }

    // ---------------------------------------------------------------------

    public boolean isAutenticado() { return usuario != null; }
    public boolean isPrimerAccesoPendiente() { return pendiente != null; }
    public Identidad getPendiente() { return pendiente; }
    public UsuarioVista getUsuario() { return usuario; }
    public UUID getIdUsuario() { return usuario == null ? null : usuario.getId(); }

    /** student | coordinator | head (los códigos que ya usan las vistas). */
    public String getRol() {
        return usuario == null ? "student" : usuario.getRol();
    }

    /**
     * La pantalla donde trabaja cada rol, para que "Inicio" no sea un segundo camino a lo mismo:
     * coordinación trabaja en su bandeja y jefatura en el seguimiento. Lo usa vista/inicio
     * (f:viewAction): null = quedarse (estudiante).
     */
    public String inicioDelRol() {
        switch (getRol()) {
            case "coordinator": return "/vista/bandeja?faces-redirect=true";
            case "head":        return "/vista/seguimiento-volumen?faces-redirect=true";
            default:            return null;
        }
    }

    /** ¿Está configurado el proveedor institucional (OIDC)? */
    public boolean isAccesoInstitucional() {
        return oidc.isConfigurado();
    }

    public boolean isModoDesarrollo() {
        return FacesContext.getCurrentInstance().getApplication().getProjectStage() == ProjectStage.Development;
    }

    /** Notificaciones sin leer para la campana. Se consulta una sola vez por petición. */
    public long getSinLeer() {
        if (usuario == null) {
            return 0;
        }
        Map<Object, Object> atributos = FacesContext.getCurrentInstance().getAttributes();
        Long n = (Long) atributos.get(CLAVE_SIN_LEER);
        if (n == null) {
            n = notificacionService.sinLeer(usuario.getId());
            atributos.put(CLAVE_SIN_LEER, n);
        }
        return n;
    }

    /** Lo llaman los controles que marcan avisos como leídos, para que la campana se actualice. */
    void olvidarSinLeer() {
        FacesContext.getCurrentInstance().getAttributes().remove(CLAVE_SIN_LEER);
    }

    /** f:viewAction del login: muestra (una vez) por qué regresó el acceso institucional. */
    public void mostrarAviso() {
        if (aviso != null) {
            Mensajes.error(aviso);
            aviso = null;
        }
    }
    public void setAviso(String aviso) { this.aviso = aviso; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    private static ExternalContext externo() {
        return FacesContext.getCurrentInstance().getExternalContext();
    }
}
