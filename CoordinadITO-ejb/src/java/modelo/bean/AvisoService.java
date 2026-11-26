package modelo.bean;

import acceso.datos.AvisoRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import modelo.entity.Aviso;
import modelo.entity.Carrera;
import modelo.entity.Fechas;
import modelo.entity.Usuario;

/**
 * «servicio» Avisos oficiales a la carrera (HU 5.3): el canal que sustituye a los grupos
 * no institucionales. Coordinación publica a sus carreras; jefatura, a cualquiera.
 */
@Stateless
public class AvisoService {

    static final int MAX_HISTORIAL = 100;

    @EJB private AvisoRepository avisos;
    @EJB private CatalogoService catalogo;
    @EJB private Guardia guardia;

    public Aviso publicar(UUID idAutor, String titulo, String contenido, LocalDate inicio, LocalDate fin, Set<UUID> idsCarreras) {
        Usuario autor = guardia.exigir(idAutor, Permiso.ATENDER);
        Set<Carrera> carreras = catalogo.carrerasPorId(idsCarreras);
        if (carreras.isEmpty()) {
            throw new ReglaNegocioException("Elige al menos una carrera.");
        }
        for (Carrera c : carreras) {
            guardia.exigirCarrera(autor, c);
        }
        LocalDate desde = inicio == null ? Fechas.hoy() : inicio;
        Reglas.requerido(fin, "Indica hasta cuándo estará vigente.");
        Reglas.periodo(desde, fin);
        if (fin.isBefore(Fechas.hoy())) {
            throw new ReglaNegocioException("La vigencia ya habría terminado: elige una fecha final a partir de hoy.");
        }
        Aviso a = new Aviso(autor, Reglas.texto(titulo, "El título", 150), Reglas.texto(contenido, "El contenido"),
                desde, fin, carreras);
        avisos.guardar(a);
        return a;
    }

    /** HU 5.3: lo que ve al entrar cada quien. Estudiante: su carrera; personal: sus carreras. */
    public List<Aviso> vigentes(UUID idUsuario) {
        return avisos.para(carrerasQueVe(guardia.usuario(idUsuario)), Fechas.hoy(), MAX_HISTORIAL);
    }

    /** HU 5.3: al concluir su vigencia el aviso deja de mostrarse, pero sigue en el historial. */
    public List<Aviso> historial(UUID idUsuario) {
        return avisos.para(carrerasQueVe(guardia.usuario(idUsuario)), null, MAX_HISTORIAL);
    }

    /** Termina la vigencia hoy. Solo quien lo publicó o jefatura. */
    public Aviso retirar(UUID idActor, UUID idAviso) {
        Usuario actor = guardia.exigir(idActor, Permiso.ATENDER);
        Aviso a = avisos.buscar(idAviso).orElseThrow(() -> new NoEncontradoException("El aviso no existe."));
        if (!actor.esJefe() && !a.getAutor().equals(actor)) {
            throw new AccesoDenegadoException("Solo quien publicó el aviso o jefatura pueden retirarlo.");
        }
        a.retirar();
        return a;
    }

    /** De estos avisos, cuáles ya leyó la persona (para el contador y el tono gris del inicio). */
    public Set<UUID> leidos(UUID idUsuario, Collection<UUID> idsAvisos) {
        return avisos.leidosPor(guardia.usuario(idUsuario).getId(), idsAvisos);
    }

    /** La persona abrió el aviso: deja de contar como pendiente. Solo avisos que le corresponden. */
    public void marcarLeido(UUID idUsuario, UUID idAviso) {
        Usuario u = guardia.usuario(idUsuario);
        Aviso a = avisos.buscar(idAviso).orElseThrow(() -> new NoEncontradoException("El aviso no existe."));
        Set<Carrera> suyas = carrerasQueVe(u);
        if (suyas != null && Collections.disjoint(suyas, a.getCarreras())) {
            throw new AccesoDenegadoException("Ese aviso no es para tu carrera.");
        }
        avisos.marcarLeido(u.getId(), a.getId());
    }

    /** null = todas las carreras (jefatura). */
    private static Set<Carrera> carrerasQueVe(Usuario u) {
        if (u.esEstudiante()) {
            return u.getCarrera() == null ? Collections.<Carrera>emptySet() : Collections.singleton(u.getCarrera());
        }
        return u.esCoordinador() ? u.getCarrerasAsignadas() : null;
    }
}
