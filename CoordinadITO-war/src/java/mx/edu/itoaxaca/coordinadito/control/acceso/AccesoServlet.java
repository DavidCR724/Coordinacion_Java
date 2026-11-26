package mx.edu.itoaxaca.coordinadito.control.acceso;

import jakarta.ejb.EJB;
import jakarta.ejb.EJBException;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import mx.edu.itoaxaca.coordinadito.control.SesionControl;
import modelo.bean.AccesoService;
import modelo.bean.ReglaNegocioException;

/**
 * Las dos rutas del acceso institucional:
 *   /acceso/iniciar   guarda el intento en la sesión y manda al proveedor.
 *   /acceso/retorno   recibe el código, lo canjea, decide con AccesoService y abre la sesión.
 * Cualquier problema regresa al login con un mensaje (nunca con el detalle técnico).
 */
@WebServlet("/acceso/*")
public class AccesoServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(AccesoServlet.class.getName());
    private static final String INTENTO = "coordinadito.oidc.intento";

    @Inject private ClienteOidc cliente;
    @Inject private ConfiguracionOidc config;
    @Inject private SesionControl sesion;
    @EJB private AccesoService accesoService;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String ruta = req.getPathInfo() == null ? "" : req.getPathInfo();
        if (!config.isConfigurado()) {
            volverAlLogin(req, res, "El acceso con cuenta institucional aún no está configurado.");
        } else if ("/iniciar".equals(ruta)) {
            iniciar(req, res);
        } else if ("/retorno".equals(ruta)) {
            retorno(req, res);
        } else {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void iniciar(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String porDefecto = req.getRequestURL().toString().replaceAll("/iniciar$", "/retorno");
            ClienteOidc.Intento intento = cliente.nuevoIntento(porDefecto);
            req.getSession().setAttribute(INTENTO, intento);
            res.sendRedirect(cliente.urlAutorizacion(intento));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "No se pudo iniciar el acceso institucional", e);
            volverAlLogin(req, res, "No se pudo contactar al proveedor de la cuenta institucional. Intenta más tarde.");
        }
    }

    private void retorno(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession s = req.getSession();
        ClienteOidc.Intento intento = (ClienteOidc.Intento) s.getAttribute(INTENTO);
        s.removeAttribute(INTENTO);   // un intento se usa una sola vez
        if (req.getParameter("error") != null) {
            volverAlLogin(req, res, "Se canceló el acceso con la cuenta institucional.");
            return;
        }
        try {
            modelo.bean.Identidad identidad = cliente.canjear(intento, req.getParameter("state"), req.getParameter("code"));
            String destino = sesion.abrir(accesoService.ingresar(identidad), req);
            res.sendRedirect(req.getContextPath() + destino);
        } catch (ReglaNegocioException e) {
            volverAlLogin(req, res, e.getMessage());
        } catch (EJBException e) {
            Throwable causa = e.getCause();
            if (causa instanceof ReglaNegocioException) {
                volverAlLogin(req, res, causa.getMessage());
            } else {
                LOG.log(Level.SEVERE, "Error al abrir la sesión", e);
                volverAlLogin(req, res, "Ocurrió un error al abrir tu sesión. Intenta de nuevo.");
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Acceso institucional rechazado", e);
            volverAlLogin(req, res, "No se pudo validar tu cuenta institucional. Intenta de nuevo.");
        }
    }

    private void volverAlLogin(HttpServletRequest req, HttpServletResponse res, String mensaje) throws IOException {
        sesion.setAviso(mensaje);
        res.sendRedirect(req.getContextPath() + "/vista/login.xhtml");
    }
}
