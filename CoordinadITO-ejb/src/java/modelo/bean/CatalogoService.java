package modelo.bean;

import acceso.datos.CarreraRepository;
import acceso.datos.MateriaRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import modelo.entity.Carrera;
import modelo.entity.Materia;

/** «servicio» Catálogos de solo lectura que usan varias pantallas: carreras y materias. */
@Stateless
public class CatalogoService {

    @EJB private CarreraRepository carreras;
    @EJB private MateriaRepository materias;

    public List<Carrera> carreras() {
        return carreras.ordenadas();
    }

    public List<Materia> materiasDe(Carrera carrera) {
        return materias.deCarrera(carrera);
    }

    /** Convierte ids de carrera en entidades; un id inexistente es un error de la petición. */
    public Set<Carrera> carrerasPorId(Collection<UUID> ids) {
        Set<Carrera> r = new HashSet<>();
        if (ids != null) {
            for (UUID id : ids) {
                r.add(carreras.buscar(id).orElseThrow(() -> new NoEncontradoException("Una de las carreras no existe.")));
            }
        }
        return r;
    }
}
