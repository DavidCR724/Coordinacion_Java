package mx.edu.itoaxaca.coordinadito.control;

import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import mx.edu.itoaxaca.coordinadito.control.vista.ComunicadoVista;
import mx.edu.itoaxaca.coordinadito.control.vista.Mensajes;
import mx.edu.itoaxaca.coordinadito.control.vista.Opcion;
import modelo.bean.AvisoService;
import modelo.bean.CatalogoService;
import modelo.entity.Aviso;
import modelo.entity.Carrera;
import modelo.entity.Fechas;

/**
 * «control» Avisos oficiales de la carrera (HU 5.3): lo que ve el alumno (vista/avisos.xhtml y el
 * inicio), lo publicado por coordinación y jefatura, y el formulario con vista previa
 * (vista/aviso-nuevo.xhtml).
 */
@Named
@ViewScoped
public class AvisosControl extends ControlBase {

    private static final long serialVersionUID = 1L;

    @EJB private AvisoService avisoService;
    @EJB private CatalogoService catalogoService;

    private List<ComunicadoVista> vigentes;
    private List<ComunicadoVista> historial;
    private boolean desplegado;
    private String abierto;
    /** id de carrera → clave, solo las que puede elegir quien publica. */
    private Map<String, String> permitidas;

    // Formulario
    private String titulo;
    private String contenido;
    private List<String> carreras = new ArrayList<>();
    /** Propuesta: visible desde hoy y durante una semana (se puede cambiar). */
    private LocalDate inicio = Fechas.hoy();
    private LocalDate fin = Fechas.hoy().plusDays(7);
    private String publicado;

    public List<ComunicadoVista> getVigentes() {
        if (vigentes == null) {
            vigentes = cargar(() -> avisoService.vigentes(yo()));
            marcarLeidos(vigentes);
        }
        return vigentes;
    }

    /** Avisos vigentes que quien mira aún no abre: el número del globo del sobre. */
    public long getNoLeidos() {
        return getVigentes().stream().filter(a -> !a.isLeido()).count();
    }

    /** ¿Se ve la lista? Siempre empieza plegada: el sobre con su número ya dice si hay algo nuevo. */
    public boolean isDesplegado() {
        return desplegado;
    }

    public void alternar() {
        desplegado = !desplegado;
    }

    /** Abre o cierra un aviso; al abrirlo por primera vez queda como leído. */
    public void abrir(String id) {
        abierto = id.equals(abierto) ? null : id;
        for (ComunicadoVista a : getVigentes()) {
            if (a.getId().equals(id) && !a.isLeido()) {
                if (Mensajes.intentar(() -> avisoService.marcarLeido(yo(), UUID.fromString(id)))) {
                    a.setLeido(true);
                }
            }
        }
    }

    public String getAbierto() { return abierto; }

    private void marcarLeidos(List<ComunicadoVista> lista) {
        if (lista.isEmpty() || !esRol("student")) {
            for (ComunicadoVista a : lista) {
                a.setLeido(true);
            }
            return;
        }
        List<UUID> ids = lista.stream().map(a -> UUID.fromString(a.getId())).collect(Collectors.toList());
        Set<UUID> leidos = Mensajes.intentar(() -> avisoService.leidos(yo(), ids));
        for (ComunicadoVista a : lista) {
            a.setLeido(leidos != null && leidos.contains(UUID.fromString(a.getId())));
        }
    }

    /** Todo lo publicado para las carreras de quien mira (también lo concluido), del más reciente al más antiguo. */
    public List<ComunicadoVista> getHistorial() {
        if (historial == null) {
            historial = cargar(() -> avisoService.historial(yo()));
        }
        return historial;
    }

    /** Estudiante: lo que ya concluyó, consultable en «Avisos anteriores». */
    public List<ComunicadoVista> getAnteriores() {
        return getHistorial().stream().filter(a -> !a.isVigente() && !a.isProgramado()).collect(Collectors.toList());
    }

    public long getNumeroVigentes() {
        return getHistorial().stream().filter(ComunicadoVista::isVigente).count();
    }

    /** Termina hoy la vigencia de un aviso (autor o jefatura). */
    public void retirar(String id) {
        if (hecho(() -> avisoService.retirar(yo(), UUID.fromString(id)), "Aviso retirado",
                "Desde mañana ya no se mostrará a los estudiantes; sigue en el historial.")) {
            vigentes = null;
            historial = null;
        }
    }

    public boolean puedeRetirar(ComunicadoVista a) {
        return a.isVigente() && (esRol("head") || yo().toString().equals(a.getIdAutor()));
    }

    // ---------------- Publicar ----------------

    /** Carreras que puede elegir: las asignadas (coordinación) o todas (jefatura). */
    public List<Opcion> getCarrerasPermitidas() {
        return Opcion.de(permitidas().entrySet(), Map.Entry::getKey, Map.Entry::getValue);
    }

    public void publicar() {
        Set<UUID> ids = carreras.stream().map(UUID::fromString).collect(Collectors.toCollection(HashSet::new));
        Aviso a = Mensajes.intentar(() -> avisoService.publicar(yo(), titulo, contenido, inicio, fin, ids));
        if (a != null) {
            publicado = a.getTitulo();
        }
    }

    /** Lo que verán los estudiantes, con lo escrito hasta el momento. */
    public ComunicadoVista getPrevia() {
        List<String> claves = carreras.stream().map(permitidas()::get).collect(Collectors.toList());
        return new ComunicadoVista(titulo, contenido, sesion.getUsuario().getNombreCompleto(), claves,
                inicio == null ? Fechas.hoy() : inicio, fin);
    }

    public boolean isPuedePublicar() {
        return esRol("coordinator") || esRol("head");
    }

    // ---------------------------------------------------------------------

    private Map<String, String> permitidas() {
        if (permitidas == null) {
            List<String> mias = sesion.getUsuario().getCarreras();
            permitidas = new LinkedHashMap<>();
            for (Carrera c : catalogoService.carreras()) {
                if (esRol("head") || mias.contains(c.getClave())) {
                    permitidas.put(c.getId().toString(), c.getClave());
                }
            }
        }
        return permitidas;
    }

    private static List<ComunicadoVista> cargar(Supplier<List<Aviso>> consulta) {
        List<Aviso> r = Mensajes.intentar(consulta);
        return r == null ? Collections.<ComunicadoVista>emptyList()
                : r.stream().map(ComunicadoVista::new).collect(Collectors.toList());
    }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public List<String> getCarreras() { return carreras; }
    public void setCarreras(List<String> carreras) { this.carreras = carreras == null ? new ArrayList<String>() : carreras; }
    public LocalDate getInicio() { return inicio; }
    public void setInicio(LocalDate inicio) { this.inicio = inicio; }
    public LocalDate getFin() { return fin; }
    public void setFin(LocalDate fin) { this.fin = fin; }
    public String getPublicado() { return publicado; }
    public LocalDate getHoy() { return Fechas.hoy(); }
}
