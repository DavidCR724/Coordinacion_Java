package modelo.bean;

import acceso.datos.SolicitudRepository;
import acceso.datos.TramiteRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import modelo.entity.Accion;
import modelo.entity.CampoFormulario;
import modelo.entity.Prioridad;
import modelo.entity.TipoCampo;
import modelo.entity.Tramite;
import modelo.entity.Usuario;

/**
 * «servicio» Catálogo de trámites: consulta (HU 1.1 a 1.4), periodos (1.3), prioridad (3.2)
 * y catálogo configurable con su formulario (8.3).
 *
 * Todo cambio pasa por {@link #modificar}: valida que sea jefatura, carga el trámite,
 * aplica el cambio y deja constancia en la auditoría. Así cada operación solo escribe su regla.
 */
@Stateless
public class TramiteService {

    private static final String ENTIDAD = "TRAMITE";

    @EJB private TramiteRepository tramites;
    @EJB private SolicitudRepository solicitudes;
    @EJB private Guardia guardia;
    @EJB private AuditoriaService auditoria;

    // ---------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------

    /** HU 1.1 y 1.4: catálogo del alumno. Cada trámite sabe su disponibilidad (getDisponibilidad()). */
    public List<Tramite> catalogo() {
        return tramites.activos();
    }

    /** HU 1.3 y 3.2: lista completa para jefatura. */
    public List<Tramite> todos() {
        return tramites.todosOrdenados();
    }

    /** HU 1.2: trámite con su formulario propio, ya ordenado. */
    public Tramite formulario(UUID idTramite) {
        return tramites.conCampos(idTramite).orElseThrow(() -> new NoEncontradoException("El trámite no existe."));
    }

    /** HU 8.3: copia editable de un trámite (o uno en blanco si id es null). */
    public TramiteDatos paraEditar(UUID idTramite, UUID idJefe) {
        guardia.exigir(idJefe, Permiso.CONFIGURAR);
        return idTramite == null ? new TramiteDatos() : TramiteDatos.de(formulario(idTramite));
    }

    /** HU 3.2: cuántas solicitudes abiertas tiene cada trámite (conservan la prioridad con que se registraron). */
    public Map<UUID, Long> abiertasPorTramite() {
        return solicitudes.abiertasPorTramite();
    }

    // ---------------------------------------------------------------------
    // Catálogo configurable (HU 8.3)
    // ---------------------------------------------------------------------

    /** Crea o actualiza un trámite completo con su formulario. */
    public Tramite guardar(TramiteDatos d, UUID idJefe) {
        validar(d);
        if (d.isNuevo()) {
            Usuario jefe = guardia.exigir(idJefe, Permiso.CONFIGURAR);
            exigirClaveLibre(d.getClave(), null);
            Tramite t = new Tramite();
            copiar(d, t);
            tramites.guardar(t);
            auditoria.registrar(jefe, Accion.CONFIGURAR_TRAMITE, ENTIDAD, t.getId(), "cambio", "alta", "clave", t.getClave());
            return t;
        }
        return modificar(idJefe, d.getId(), "datos y formulario", t -> {
            exigirClaveLibre(d.getClave(), t);
            copiar(d, t);
        });
    }

    /** Un trámite sin solicitudes se puede borrar; con solicitudes solo se desactiva (HU 8.3). */
    public void eliminar(UUID idTramite, UUID idJefe) {
        Usuario jefe = guardia.exigir(idJefe, Permiso.CONFIGURAR);
        Tramite t = existente(idTramite);
        if (tramites.tieneSolicitudes(t)) {
            throw new ReglaNegocioException("«" + t.getNombre() + "» ya tiene solicitudes registradas: desactívalo en lugar de eliminarlo.");
        }
        auditoria.registrar(jefe, Accion.CONFIGURAR_TRAMITE, ENTIDAD, t.getId(), "cambio", "baja", "clave", t.getClave());
        tramites.borrar(t);
    }

    // ---------------------------------------------------------------------
    // Internos
    // ---------------------------------------------------------------------

    /** Plantilla de todo cambio: permiso → carga → cambio → auditoría. */
    private Tramite modificar(UUID idJefe, UUID idTramite, String queCambio, Consumer<Tramite> cambio) {
        Usuario jefe = guardia.exigir(idJefe, Permiso.CONFIGURAR);
        Tramite t = existente(idTramite);
        cambio.accept(t);
        auditoria.registrar(jefe, Accion.CONFIGURAR_TRAMITE, ENTIDAD, t.getId(), "cambio", queCambio, "clave", t.getClave());
        return t;
    }

    private Tramite existente(UUID id) {
        return tramites.buscar(id).orElseThrow(() -> new NoEncontradoException("El trámite no existe."));
    }

    private void exigirClaveLibre(String clave, Tramite propio) {
        tramites.porClave(clave.trim().toUpperCase()).ifPresent(otro -> {
            if (!otro.equals(propio)) {
                throw new ReglaNegocioException("Ya existe un trámite con la clave " + otro.getClave() + ".");
            }
        });
    }

    private static void validar(TramiteDatos d) {
        String clave = Reglas.texto(d.getClave(), "La clave", 10);
        if (!clave.matches("[A-Za-z0-9-]+")) {
            throw new ReglaNegocioException("La clave solo admite letras, números y guiones.");
        }
        Reglas.texto(d.getNombre(), "El nombre", 150);
        Reglas.opcional(d.getDescripcion(), "La descripción");
        Reglas.opcional(d.getRequisitos(), "Los requisitos");
        Reglas.requerido(d.getPrioridad(), "Elige la prioridad por defecto.");
        Reglas.periodo(d.getInicio(), d.getFin());
        if (d.getDiasEsperados() == null || d.getDiasEsperados() < 1 || d.getDiasEsperados() > 180) {
            throw new ReglaNegocioException("Los días esperados deben estar entre 1 y 180.");
        }
        for (CampoDatos c : d.getCampos()) {
            String etiqueta = Reglas.texto(c.getEtiqueta(), "La etiqueta de cada campo", 150);
            Reglas.opcional(c.getAyuda(), "La ayuda de «" + etiqueta + "»", 255);
            if (c.getLongitudMax() != null && c.getLongitudMax() < 1) {
                throw new ReglaNegocioException("«" + etiqueta + "»: la longitud máxima debe ser mayor que cero.");
            }
            if (c.getTipo() == TipoCampo.SELECCION && Reglas.limpio(c.getOpciones()) == null) {
                throw new ReglaNegocioException("«" + etiqueta + "»: escribe las opciones, una por línea.");
            }
        }
    }

    /**
     * Pasa los datos al trámite. Los campos se reordenan según la lista; un campo que ya no viene
     * se desactiva (no se borra) para no perder las respuestas de solicitudes anteriores.
     */
    private void copiar(TramiteDatos d, Tramite t) {
        t.setClave(d.getClave().trim().toUpperCase());
        t.setNombre(d.getNombre().trim());
        t.setDescripcion(Reglas.limpio(d.getDescripcion()));
        t.setRequisitos(Reglas.limpio(d.getRequisitos()));
        t.setPrioridad(d.getPrioridad());
        t.setDocumentoRequerido(d.isDocumentoRequerido());
        t.setColectivo(d.isColectivo());
        t.setInicioPeriodo(d.getInicio());
        t.setFinPeriodo(d.getFin());
        t.setDiasEsperados(d.getDiasEsperados().shortValue());
        t.setActivo(d.isActivo());

        Map<UUID, CampoFormulario> actuales = new HashMap<>();
        for (CampoFormulario c : t.getCampos()) {
            actuales.put(c.getId(), c);
        }
        // Primero se mueven todos a órdenes libres: la base exige ORDEN único por trámite.
        short temporal = 1000;
        for (CampoFormulario c : t.getCampos()) {
            c.setOrden(temporal++);
        }
        tramites.sincronizar();
        List<CampoFormulario> enUso = new ArrayList<>();
        short orden = 1;
        for (CampoDatos cd : d.getCampos()) {
            CampoFormulario c = cd.getId() == null ? null : actuales.get(cd.getId());
            if (c == null) {
                c = new CampoFormulario(cd.getEtiqueta().trim(), cd.getTipo(), cd.isObligatorio(), orden);
                t.agregarCampo(c);
            }
            c.setEtiqueta(cd.getEtiqueta().trim());
            c.setTipo(cd.getTipo());
            c.setObligatorio(cd.isObligatorio());
            c.setAyuda(Reglas.limpio(cd.getAyuda()));
            c.setLongitudMax(cd.getLongitudMax());
            c.setOpciones(cd.getTipo() == TipoCampo.SELECCION ? cd.getOpciones().trim() : null);
            c.setActivo(cd.isActivo());
            c.setOrden(orden++);
            enUso.add(c);
        }
        for (CampoFormulario c : t.getCampos()) {
            if (!enUso.contains(c)) {
                c.setActivo(false);
                c.setOrden(orden++);
            }
        }
    }
}
