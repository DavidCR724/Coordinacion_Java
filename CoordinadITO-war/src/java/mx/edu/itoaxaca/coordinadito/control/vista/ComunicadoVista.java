package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import modelo.entity.Aviso;
import modelo.entity.Carrera;
import modelo.entity.Fechas;

/**
 * Un aviso oficial de la carrera (HU 5.3) como lo pinta comp-tarjeta-aviso.
 * Se arma igual desde un Aviso guardado que desde el borrador que se está escribiendo (vista previa),
 * así la tarjeta y la previa nunca difieren.
 */
public class ComunicadoVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String idAutor;
    private final String titulo;
    private final String contenido;
    private final String autor;
    private final List<String> carreras;
    private final String inicio;
    private final String fin;
    private final String publicado;
    private final boolean vigente;
    private final boolean programado;
    /** Si quien mira ya lo abrió (AVISO_LECTURA). Lo fija AvisosControl; la vista previa queda en false. */
    private boolean leido;

    /** Borrador de la vista previa (o base del aviso guardado). */
    public ComunicadoVista(String titulo, String contenido, String autor, Collection<String> claves,
                           LocalDate inicio, LocalDate fin) {
        this(null, null, titulo, contenido, autor, claves, inicio, fin, null);
    }

    public ComunicadoVista(Aviso a) {
        this(a.getId().toString(), a.getAutor().getId().toString(), a.getTitulo(), a.getContenido(),
             a.getAutor().getNombreCompleto(), claves(a.getCarreras()), a.getInicioVigencia(), a.getFinVigencia(),
             Formato.fecha(a.getFechaPublicacion()));
    }

    private ComunicadoVista(String id, String idAutor, String titulo, String contenido, String autor,
                            Collection<String> claves, LocalDate inicio, LocalDate fin, String publicado) {
        this.id = id;
        this.idAutor = idAutor;
        this.titulo = titulo;
        this.contenido = contenido;
        this.autor = autor;
        List<String> c = claves == null ? new ArrayList<String>() : new ArrayList<>(claves);
        Collections.sort(c);
        this.carreras = Collections.unmodifiableList(c);
        this.inicio = inicio == null ? "—" : Formato.fecha(inicio);
        this.fin = fin == null ? "—" : Formato.fecha(fin);
        this.publicado = publicado;
        LocalDate hoy = Fechas.hoy();
        this.programado = inicio != null && inicio.isAfter(hoy);
        this.vigente = !programado && (fin == null || !fin.isBefore(hoy));
    }

    private static List<String> claves(Collection<Carrera> carreras) {
        List<String> r = new ArrayList<>();
        for (Carrera c : carreras) {
            r.add(c.getClave());
        }
        return r;
    }

    public String getId() { return id; }
    public String getIdAutor() { return idAutor; }
    public String getTitulo() { return titulo; }
    public String getContenido() { return contenido; }
    public String getAutor() { return autor; }
    public List<String> getCarreras() { return carreras; }
    public String getInicio() { return inicio; }
    public String getFin() { return fin; }
    public String getPublicado() { return publicado; }
    public boolean isVigente() { return vigente; }
    public boolean isLeido() { return leido; }
    public void setLeido(boolean leido) { this.leido = leido; }
    /** Publicado, pero su vigencia empieza después. */
    public boolean isProgramado() { return programado; }
    public String getEstado() { return vigente ? "Vigente" : (programado ? "Programado" : "Concluido"); }
    public String getIcono() { return "megaphone"; }
    public String getTono() { return vigente ? "tono-naranja" : ""; }
}
