package acceso.datos;

import jakarta.ejb.Stateless;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import modelo.entity.Accion;
import modelo.entity.Carrera;
import modelo.entity.RegistroAuditoria;

/** «acceso a datos» Bitácora de auditoría (HU 8.2). Solo inserta y consulta. */
@Stateless
public class AuditoriaRepository extends RepositorioBase<RegistroAuditoria, Long> {

    public AuditoriaRepository() {
        super(RegistroAuditoria.class);
    }

    /** carreras: null = toda la bitácora (jefatura); si no, lo que hicieron personas de esas carreras. */
    public List<RegistroAuditoria> buscar(Accion accion, OffsetDateTime desde, OffsetDateTime hasta, String texto,
                                          Collection<Carrera> carreras, int inicio, int maximo) {
        return filtro(accion, desde, hasta, texto, carreras).lista(em, "SELECT r FROM RegistroAuditoria r LEFT JOIN r.actor u",
                "ORDER BY r.fecha DESC, r.id DESC", RegistroAuditoria.class, inicio, maximo);
    }

    public long contar(Accion accion, OffsetDateTime desde, OffsetDateTime hasta, String texto, Collection<Carrera> carreras) {
        return filtro(accion, desde, hasta, texto, carreras).contar(em, "SELECT COUNT(r) FROM RegistroAuditoria r LEFT JOIN r.actor u");
    }

    private static Consulta filtro(Accion accion, OffsetDateTime desde, OffsetDateTime hasta, String texto,
                                   Collection<Carrera> carreras) {
        Consulta c = new Consulta();
        if (carreras != null) {
            // Quien actuó es alumno de esas carreras o coordinación de alguna de ellas.
            // Los intentos sin cuenta (actor nulo) solo los ve jefatura.
            c = carreras.isEmpty() ? c.y("1 = 0") : c.y("(u.carrera IN :carrerasAud OR EXISTS (SELECT u2 FROM Usuario u2"
                    + " JOIN u2.carrerasAsignadas ca WHERE u2 = u AND ca IN :carrerasAud))", "carrerasAud", carreras);
        }
        return c
                .siHay("r.accion = :accion", "accion", accion)
                .siHay("r.fecha >= :desde", "desde", desde)
                .siHay("r.fecha < :hasta", "hasta", hasta)
                .texto(texto, null, "u.correo", "u.nombres", "u.apellidoPaterno", "r.idEntidad", "r.detalle");
    }
}
