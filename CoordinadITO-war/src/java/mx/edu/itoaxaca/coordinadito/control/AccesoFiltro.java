package mx.edu.itoaxaca.coordinadito.control;

import jakarta.inject.Inject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import modelo.bean.ContextoPeticion;

/**
 * Portero de las vistas y origen de cada petición.
 *   1. Deja la IP de la petición en {@link ContextoPeticion} para la auditoría (HU 8.2).
 *   2. Sin sesión, cualquier página de /vista/ (menos las públicas) manda al login.
 *   3. Con el primer acceso pendiente (HU 6.4), la única página permitida es primer-acceso.
 * Qué puede hacer cada rol lo decide el EJB en cada operación, no este filtro.
 */
@WebFilter(urlPatterns = "/*")
public class AccesoFiltro extends HttpFilter {

    private static final long serialVersionUID = 1L;

    private static final Set<String> PUBLICAS = new HashSet<>(Arrays.asList(
            "/vista/login.xhtml", "/vista/error.xhtml", "/vista/acceso-denegado.xhtml"));
    private static final String PRIMER_ACCESO = "/vista/primer-acceso.xhtml";
    /**
     * Muestra el nombre de la base, el usuario de conexión y las tablas. En desarrollo es pública
     * (sirve antes de poder entrar); en producción, solo jefatura.
     */
    private static final String DIAGNOSTICO = "/vista/diagnostico.xhtml";

    @Inject
    private SesionControl sesion;

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        ContextoPeticion.iniciar(req.getRemoteAddr());
        cabecerasDeSeguridad(res);
        try {
            String ruta = req.getRequestURI().substring(req.getContextPath().length());
            String destino = destinoObligado(ruta);
            if (destino == null) {
                if (ruta.startsWith("/vista/") && !PUBLICAS.contains(ruta)) {
                    // Que el botón «atrás» no muestre páginas privadas después de salir.
                    res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                }
                chain.doFilter(req, res);
            } else {
                redirigir(req, res, req.getContextPath() + destino);
            }
        } finally {
            ContextoPeticion.terminar();
        }
    }

    /** Página a la que hay que mandar al usuario, o null si puede ver la que pidió. */
    private String destinoObligado(String ruta) {
        if (!ruta.startsWith("/vista/") || PUBLICAS.contains(ruta)) {
            return null;
        }
        if (ruta.equals(DIAGNOSTICO)) {
            if (desarrollo()) {
                return null;
            }
            if (!sesion.isAutenticado()) {
                return "/vista/login.xhtml";
            }
            return "head".equals(sesion.getRol()) ? null : "/vista/acceso-denegado.xhtml";
        }
        if (sesion.isAutenticado()) {
            return ruta.equals(PRIMER_ACCESO) ? "/vista/inicio.xhtml" : null;
        }
        if (sesion.isPrimerAccesoPendiente()) {
            return ruta.equals(PRIMER_ACCESO) ? null : PRIMER_ACCESO;
        }
        return "/vista/login.xhtml";
    }

    private boolean desarrollo() {
        return "Development".equalsIgnoreCase(getServletContext().getInitParameter("jakarta.faces.PROJECT_STAGE"));
    }

    /**
     * Cabeceras que le dicen al navegador qué NO permitir:
     *  - nosniff: no «adivinar» el tipo de un archivo (un adjunto no se ejecuta como script).
     *  - frame-ancestors / X-Frame-Options: nadie puede incrustar el portal en su página (clickjacking).
     *  - form-action, base-uri, object-src: los formularios solo envían a este sitio; sin plugins.
     *  - Referrer-Policy: al salir a otro sitio no se manda la URL completa (que lleva el folio).
     * No se restringen los scripts en línea porque PrimeFaces los necesita.
     */
    private static void cabecerasDeSeguridad(HttpServletResponse res) {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "SAMEORIGIN");
        res.setHeader("Content-Security-Policy", "frame-ancestors 'self'; form-action 'self'; base-uri 'self'; object-src 'none'");
        res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    }

    /** Una petición AJAX de PrimeFaces no sigue un 302: se le responde con un redirect de Faces. */
    private static void redirigir(HttpServletRequest req, HttpServletResponse res, String url) throws IOException {
        if ("partial/ajax".equals(req.getHeader("Faces-Request"))) {
            res.setContentType("text/xml");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().printf("<?xml version=\"1.0\" encoding=\"UTF-8\"?><partial-response><redirect url=\"%s\"/></partial-response>", url);
        } else {
            res.sendRedirect(url);
        }
    }
}
