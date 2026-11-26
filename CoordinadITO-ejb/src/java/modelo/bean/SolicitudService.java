package modelo.bean;

import acceso.datos.SolicitudRepository;
import acceso.datos.TramiteRepository;
import acceso.datos.UsuarioRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import modelo.entity.CampoFormulario;
import modelo.entity.Carrera;
import modelo.entity.DocumentoAdjunto;
import modelo.entity.EstadoSolicitud;
import modelo.entity.Fechas;
import modelo.entity.MensajeSolicitud;
import modelo.entity.Prioridad;
import modelo.entity.Solicitud;
import modelo.entity.TipoMensaje;
import modelo.entity.Tramite;
import modelo.entity.Usuario;

/**
 * «servicio» Ciclo de vida de la solicitud: registro, consulta, bandeja, atención y bitácora.
 *
 * Historias: 1.2, 1.5, 2.1 a 2.9, 3.1, 3.3, 3.4, 4.3 (avisos a los adheridos), 5.1 y 9.2.
 * Cada método público es una transacción: si algo falla, no queda nada a medias.
 * Los permisos se piden a {@link Guardia}; las validaciones de texto, a {@link Reglas}.
 */
@Stateless
public class SolicitudService {

    /** HU 1.5: límites de los adjuntos. */
    static final long MAX_BYTES_ARCHIVO = 5L * 1024 * 1024;
    static final int MAX_ARCHIVOS = 5;
    static final Set<String> TIPOS_PERMITIDOS = new HashSet<>(Arrays.asList(
            "application/pdf", "image/jpeg", "image/png"));

    @EJB private SolicitudRepository solicitudes;
    @EJB private TramiteRepository tramites;
    @EJB private UsuarioRepository usuarios;
    @EJB private Guardia guardia;
    @EJB private NotificacionService notificaciones;
    @EJB private AlmacenArchivos almacen;

    // =====================================================================
    // Alumno
    // =====================================================================

    /**
     * HU 2.9 (aviso anticipado): el folio de la solicitud que el alumno ya tiene abierta de ese
     * trámite, o null. Sirve para avisarle ANTES de llenar el formulario; la regla la sigue
     * aplicando {@link #registrar} en el servidor.
     */
    public Integer folioAbierto(UUID idAlumno, UUID idTramite) {
        Usuario alumno = guardia.usuario(idAlumno);
        return tramites.buscar(idTramite)
                .flatMap(t -> solicitudes.abiertaDe(alumno, t))
                .map(Solicitud::getFolio)
                .orElse(null);
    }

    /**
     * HU 2.2 (+1.2, 1.4, 1.5, 2.9): registra una solicitud y devuelve su folio.
     *
     * @param respuestas valor capturado por id de campo del formulario del trámite
     */
    public Solicitud registrar(UUID idAlumno, UUID idTramite, String peticion,
                               Map<UUID, String> respuestas, List<ArchivoNuevo> archivos) {
        Usuario alumno = exigirAlumnoConCarrera(idAlumno);
        Tramite tramite = tramites.conCampos(idTramite)
                .orElseThrow(() -> new NoEncontradoException("El trámite no existe."));
        Reglas.disponible(tramite);
        if (tramite.isColectivo()) {
            throw new ReglaNegocioException("La apertura de grupo se solicita desde la sección Grupos.");
        }
        solicitudes.abiertaDe(alumno, tramite).ifPresent(s -> {
            throw new SolicitudDuplicadaException(s.getFolio());
        });

        Map<UUID, String> valores = respuestas == null ? Collections.<UUID, String>emptyMap() : respuestas;
        List<ArchivoNuevo> docs = archivos == null ? Collections.<ArchivoNuevo>emptyList() : archivos;
        validarFormulario(tramite, valores);
        validarArchivos(docs, tramite.isDocumentoRequerido());

        Solicitud s = new Solicitud(tramite, alumno, Reglas.opcional(peticion, "La explicación"));
        for (CampoFormulario c : tramite.getCamposActivos()) {
            String v = Reglas.limpio(valores.get(c.getId()));
            if (v != null) {
                s.agregarRespuesta(c, v);
            }
        }
        adjuntar(s, alumno, docs, null);
        guardarNueva(s, "uq_solicitud_abierta", () -> new SolicitudDuplicadaException(null));
        notificaciones.avisar(alumno, s, "Recibimos tu solicitud " + s.getFolioTexto(),
                "Tu solicitud de " + tramite.getNombre() + " quedó registrada. Te avisaremos de cada cambio.");
        avisarCoordinacion(s, tramite.getNombre() + " de " + alumno.getNombreCompleto() + ".");
        return s;
    }

    /** HU 2.3 y 4.3: las que registró el alumno y las aperturas de grupo a las que se adhirió. */
    public List<Solicitud> misSolicitudes(UUID idAlumno) {
        return solicitudes.deAlumno(guardia.usuario(idAlumno));
    }

    /**
     * HU 2.6: el alumno contesta una petición de información dentro de la misma solicitud.
     * La solicitud vuelve a PROCESANDO y se avisa a quien la atiende.
     */
    public Solicitud responder(UUID idAlumno, int folio, String texto, List<ArchivoNuevo> archivos) {
        Usuario alumno = guardia.usuario(idAlumno);
        Solicitud s = existente(folio);
        if (!s.getSolicitante().equals(alumno)) {
            throw new AccesoDenegadoException("Solo quien registró la solicitud puede responderla.");
        }
        if (s.getEstado() != EstadoSolicitud.ESPERA) {
            throw new ReglaNegocioException("Esta solicitud no está esperando información.");
        }
        String contenido = Reglas.texto(texto, "La respuesta");
        List<ArchivoNuevo> docs = archivos == null ? Collections.<ArchivoNuevo>emptyList() : archivos;
        validarArchivos(docs, false);
        MensajeSolicitud respuesta = s.agregarMensaje(alumno, TipoMensaje.RESPUESTA, contenido, s.getPeticionPendiente());
        adjuntar(s, alumno, docs, respuesta);
        s.cambiarEstado(EstadoSolicitud.PROCESANDO, alumno, "El alumno respondió la petición de información.");
        if (s.getAsignado() != null) {
            notificaciones.avisar(s.getAsignado(), s, "Respuesta en " + s.getFolioTexto(),
                    alumno.getNombreCompleto() + " respondió tu petición de información.");
        }
        return s;
    }

    // =====================================================================
    // Consulta (alumno, coordinación, jefatura)
    // =====================================================================

    /**
     * HU 2.3, 2.8, 4.3: detalle con respuestas, adjuntos, mensajes, bitácora, prioridad y adhesiones.
     * Quién la ve: ver {@link #puedeVer}.
     */
    public Solicitud detalle(int folio, UUID idUsuario) {
        Usuario u = guardia.usuario(idUsuario);
        Solicitud s = existente(folio);
        if (!puedeVer(u, s)) {
            throw new AccesoDenegadoException("No tienes acceso a esta solicitud.");
        }
        s.getRespuestas().size();
        s.getAdjuntos().size();
        s.getMensajes().size();
        s.getBitacora().size();
        s.getCambiosPrioridad().size();
        s.getAdhesiones().size();
        return s;
    }

    /**
     * Estudiante: las suyas, las aperturas a las que se adhirió y las aperturas de su carrera
     * (para poder adherirse). Coordinación: las de sus carreras. Jefatura: todas.
     */
    public boolean puedeVer(Usuario u, Solicitud s) {
        if (u.esEstudiante()) {
            return s.esInteresado(u) || (s.isColectiva() && s.getMateria().getCarrera().equals(u.getCarrera()));
        }
        return u.atiendeCarrera(s.getCarrera());
    }

    /** HU 1.5: contenido de un adjunto, con los mismos permisos que el detalle. */
    public byte[] descargarAdjunto(int folio, UUID idDocumento, UUID idUsuario) {
        Solicitud s = detalle(folio, idUsuario);
        for (DocumentoAdjunto d : s.getAdjuntos()) {
            if (d.getId().equals(idDocumento)) {
                return almacen.leer(d.getReferencia());
            }
        }
        throw new NoEncontradoException("El documento no pertenece a esta solicitud.");
    }

    // =====================================================================
    // Coordinación y jefatura
    // =====================================================================

    /**
     * HU 2.1, 3.1, 3.4: bandeja de quien atiende. Coordinación ve sus carreras; jefatura todas
     * (HU 2.8: con el filtro de estados puede consultar también las cerradas).
     */
    public Pagina<Solicitud> bandeja(UUID idUsuario, FiltroBandeja filtro) {
        Set<Carrera> carreras = carrerasDe(guardia.exigir(idUsuario, Permiso.ATENDER));
        FiltroBandeja f = filtro == null ? new FiltroBandeja() : filtro;
        Reglas.periodo(f.getDesde(), f.getHasta());
        List<Solicitud> filas = solicitudes.bandeja(carreras, f.getEstados(), f.getIdTramite(), f.getPrioridadMinima(),
                Fechas.inicio(f.getDesde()), Fechas.finExclusivo(f.getHasta()), f.getTexto(), f.isPorFecha(),
                f.getPagina() * f.getTamano(), f.getTamano());
        long total = solicitudes.contarBandeja(carreras, f.getEstados(), f.getIdTramite(), f.getPrioridadMinima(),
                Fechas.inicio(f.getDesde()), Fechas.finExclusivo(f.getHasta()), f.getTexto());
        return new Pagina<>(filas, total, f.getPagina(), f.getTamano());
    }

    /** Contadores del encabezado de la bandeja. */
    public ResumenBandeja resumen(UUID idUsuario) {
        Set<Carrera> carreras = carrerasDe(guardia.exigir(idUsuario, Permiso.ATENDER));
        return new ResumenBandeja(solicitudes.contarPorEstado(carreras), solicitudes.abiertasPorPrioridad(carreras));
    }

    /**
     * HU 2.4 y 2.7: cambia el estado con constancia obligatoria.
     * Para FINALIZADA o RECHAZADA la constancia es también la resolución que verán los interesados.
     * Para pedir información usa {@link #solicitarInformacion}.
     */
    public Solicitud cambiarEstado(int folio, EstadoSolicitud nuevo, String constancia, UUID idActor) {
        Usuario actor = guardia.exigir(idActor, Permiso.ATENDER);
        Solicitud s = existenteQueAtiende(folio, actor);
        Reglas.requerido(nuevo, "Elige el nuevo estado.");
        if (nuevo == EstadoSolicitud.ESPERA) {
            throw new ReglaNegocioException("Para pedir información al alumno usa «Pedir información».");
        }
        if (nuevo == EstadoSolicitud.ENVIADA) {
            throw new ReglaNegocioException("Una solicitud no puede volver a «Recibida».");
        }
        // El comentario es opcional: si se hizo lo que el alumno pidió, el estado lo dice todo.
        // Solo el rechazo exige motivo, porque el alumno necesita saber qué corregir.
        String texto = nuevo == EstadoSolicitud.RECHAZADA
                ? Reglas.texto(constancia, "El motivo del rechazo")
                : Reglas.opcional(constancia, "El comentario");
        aplicarTransicion(s, nuevo, actor, texto);
        if (nuevo.isFinal()) {
            s.setResolucion(texto);
            s.setCitaRequerida(false);
        }
        notificaciones.avisarATodos(s.getInteresados(), s,
                "Tu solicitud " + s.getFolioTexto() + " está " + nuevo.getEtiqueta().toLowerCase(),
                textoDelAviso(nuevo, texto));
        return s;
    }

    /** HU 2.5: pide al alumno información faltante. La solicitud pasa a ESPERA. */
    public Solicitud solicitarInformacion(int folio, String peticion, UUID idActor) {
        Usuario actor = guardia.exigir(idActor, Permiso.ATENDER);
        Solicitud s = existenteQueAtiende(folio, actor);
        String texto = Reglas.texto(peticion, "La petición");
        aplicarTransicion(s, EstadoSolicitud.ESPERA, actor, "Se pidió información al alumno.");
        s.agregarMensaje(actor, TipoMensaje.SOLICITUD_INFORMACION, texto, null);
        notificaciones.avisar(s.getSolicitante(), s, "Necesitamos información para " + s.getFolioTexto(), texto);
        return s;
    }

    /** HU 3.3: eleva la prioridad con motivo obligatorio. Nunca baja. */
    public Solicitud elevarPrioridad(int folio, Prioridad nueva, String motivo, UUID idActor) {
        Usuario actor = guardia.exigir(idActor, Permiso.ATENDER);
        Solicitud s = existenteQueAtiende(folio, actor);
        exigirAbierta(s);
        if (nueva == null || !nueva.esMayorQue(s.getPrioridad())) {
            throw new ReglaNegocioException("La nueva prioridad debe ser mayor que " + s.getPrioridad().getEtiqueta() + ".");
        }
        s.elevarPrioridad(nueva, actor, Reglas.texto(motivo, "El motivo"));
        return s;
    }

    /** HU 9.2: coordinación pide que el alumno se presente; él agenda la cita en un horario libre. */
    public Solicitud requerirCita(int folio, String motivo, UUID idActor) {
        Usuario actor = guardia.exigir(idActor, Permiso.ATENDER);
        Solicitud s = existenteQueAtiende(folio, actor);
        exigirAbierta(s);
        String texto = Reglas.texto(motivo, "Para qué necesitas verlo");
        s.setCitaRequerida(true);
        s.agregarMensaje(actor, TipoMensaje.NOTA, "Cita presencial: " + texto, null);
        tomar(s, actor);
        notificaciones.avisar(s.getSolicitante(), s, "Agenda una cita para " + s.getFolioTexto(),
                texto + " Elige un horario desde el detalle de tu solicitud.");
        return s;
    }

    // =====================================================================
    // Operaciones que también usa GrupoService. Son públicas porque un EJB solo puede llamar
    // métodos públicos de otro EJB (el contenedor rechaza los demás).
    // =====================================================================

    /** Estudiante activo con carrera: sin carrera no hay coordinación que lo atienda (HU 6.4). */
    public Usuario exigirAlumnoConCarrera(UUID idAlumno) {
        Usuario alumno = guardia.exigir(idAlumno, Permiso.SOLICITAR);
        if (alumno.getCarrera() == null) {
            throw new AccesoDenegadoException("Completa tu carrera y semestre antes de registrar solicitudes.");
        }
        return alumno;
    }

    /**
     * Guarda una solicitud nueva y trae su folio. Si dos envíos casi simultáneos chocan con un
     * índice único de la base, lanza la excepción de negocio que corresponde.
     */
    public void guardarNueva(Solicitud s, String indiceUnico, java.util.function.Supplier<ReglaNegocioException> siChoca) {
        solicitudes.guardar(s);
        try {
            solicitudes.recargar(s);
        } catch (PersistenceException e) {
            if (Reglas.violaRestriccion(e, indiceUnico)) {
                throw siChoca.get();
            }
            throw e;
        }
    }

    public void avisarCoordinacion(Solicitud s, String mensaje) {
        notificaciones.avisarATodos(usuarios.coordinadoresDe(s.getCarrera()), s, "Nueva solicitud " + s.getFolioTexto(), mensaje);
    }

    public Solicitud existente(int folio) {
        return solicitudes.porFolio(folio)
                .orElseThrow(() -> new NoEncontradoException(String.format("No existe la solicitud ITO-%04d.", folio)));
    }

    // =====================================================================
    // Reglas internas
    // =====================================================================

    /** HU 1.2: obligatorios, longitud y tipo de cada campo. */
    void validarFormulario(Tramite tramite, Map<UUID, String> valores) {
        Map<UUID, CampoFormulario> propios = new HashMap<>();
        for (CampoFormulario c : tramite.getCamposActivos()) {
            propios.put(c.getId(), c);
        }
        for (UUID id : valores.keySet()) {
            if (!propios.containsKey(id)) {
                throw new ReglaNegocioException("El formulario incluye un campo que no pertenece a este trámite.");
            }
        }
        for (CampoFormulario c : propios.values()) {
            String v = Reglas.limpio(valores.get(c.getId()));
            if (v == null) {
                if (c.isObligatorio()) {
                    throw new ReglaNegocioException("«" + c.getEtiqueta() + "» es obligatorio.");
                }
                continue;
            }
            if (c.getLongitudMax() != null && v.length() > c.getLongitudMax()) {
                throw new ReglaNegocioException("«" + c.getEtiqueta() + "» admite máximo " + c.getLongitudMax() + " caracteres.");
            }
            switch (c.getTipo()) {
                case NUMERO:
                    try {
                        new BigDecimal(v);
                    } catch (NumberFormatException e) {
                        throw new ReglaNegocioException("«" + c.getEtiqueta() + "» debe ser un número.");
                    }
                    break;
                case FECHA:
                    try {
                        LocalDate.parse(v);
                    } catch (DateTimeParseException e) {
                        throw new ReglaNegocioException("«" + c.getEtiqueta() + "» debe ser una fecha (aaaa-mm-dd).");
                    }
                    break;
                case SELECCION:
                    if (!c.getListaOpciones().contains(v)) {
                        throw new ReglaNegocioException("Elige una opción válida en «" + c.getEtiqueta() + "».");
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /** HU 1.5: cantidad, tamaño y tipo de los adjuntos. */
    void validarArchivos(List<ArchivoNuevo> archivos, boolean obligatorio) {
        if (obligatorio && archivos.isEmpty()) {
            throw new ReglaNegocioException("Este trámite requiere adjuntar al menos un documento.");
        }
        if (archivos.size() > MAX_ARCHIVOS) {
            throw new ReglaNegocioException("Puedes adjuntar máximo " + MAX_ARCHIVOS + " archivos.");
        }
        for (ArchivoNuevo a : archivos) {
            if (a.getTamano() == 0) {
                throw new ReglaNegocioException("El archivo «" + a.getNombre() + "» está vacío.");
            }
            if (a.getTamano() > MAX_BYTES_ARCHIVO) {
                throw new ReglaNegocioException("«" + a.getNombre() + "» pesa más de 5 MB.");
            }
            if (a.getMime() == null || !TIPOS_PERMITIDOS.contains(a.getMime().toLowerCase())) {
                throw new ReglaNegocioException("«" + a.getNombre() + "» debe ser PDF, JPG o PNG.");
            }
        }
    }

    private void adjuntar(Solicitud s, Usuario autor, List<ArchivoNuevo> docs, MensajeSolicitud mensaje) {
        for (ArchivoNuevo a : docs) {
            s.agregarAdjunto(new DocumentoAdjunto(s, autor, a.getNombre(), a.getMime(), a.getTamano(),
                    almacen.guardar(a), mensaje));
        }
    }

    /** Cambio de estado validado; quien lo hace queda como responsable si nadie lo era. */
    private static String textoDelAviso(EstadoSolicitud nuevo, String texto) {
        if (texto != null) {
            return (nuevo.isFinal() ? "Resolución: " : "Comentario de coordinación: ") + texto;
        }
        return nuevo == EstadoSolicitud.FINALIZADA
                ? "Se atendió lo que pediste. Puedes consultar el detalle en tu solicitud."
                : "La coordinación ya está revisando tu solicitud.";
    }

    private void aplicarTransicion(Solicitud s, EstadoSolicitud nuevo, Usuario actor, String comentario) {
        if (!s.getEstado().puedePasarA(nuevo)) {
            throw new ReglaNegocioException("No se puede pasar de «" + s.getEstado().getEtiqueta()
                    + "» a «" + nuevo.getEtiqueta() + "».");
        }
        s.cambiarEstado(nuevo, actor, comentario);
        tomar(s, actor);
    }

    private static void tomar(Solicitud s, Usuario actor) {
        if (s.getAsignado() == null) {
            s.setAsignado(actor);
        }
    }

    private static void exigirAbierta(Solicitud s) {
        if (!s.getEstado().isAbierto()) {
            throw new ReglaNegocioException("La solicitud ya está cerrada.");
        }
    }

    private Solicitud existenteQueAtiende(int folio, Usuario actor) {
        Solicitud s = existente(folio);
        guardia.exigirCarrera(actor, s.getCarrera());
        return s;
    }

    /** null = todas las carreras (jefatura). */
    static Set<Carrera> carrerasDe(Usuario u) {
        return u.esJefe() ? null : u.getCarrerasAsignadas();
    }
}
