package acceso.datos;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Armador de consultas JPQL con filtros opcionales (patrón Builder).
 *
 * Las pantallas de búsqueda (bandeja, grupos, alumnos, auditoría, seguimiento) tienen el mismo
 * problema: cada filtro puede venir vacío y entonces no debe aparecer en el WHERE. Este armador
 * junta las condiciones y sus parámetros en un solo lugar, y la misma instancia sirve para
 * pedir la página de resultados y el total, así nunca se desincronizan.
 *
 * <pre>
 * Consulta c = new Consulta()
 *         .y("s.estado IN :estados", "estados", estados)
 *         .siHay("s.tramite.id = :tramite", "tramite", idTramite)
 *         .dentroDe("s.carrera", carreras)
 *         .texto(busqueda, "a.nombres", "a.correo");
 * List&lt;Solicitud&gt; filas = c.lista(em, "SELECT s FROM Solicitud s JOIN s.solicitante a", "ORDER BY s.folio", Solicitud.class, 0, 20);
 * long total = c.contar(em, "SELECT COUNT(s) FROM Solicitud s JOIN s.solicitante a");
 * </pre>
 */
public final class Consulta {

    private static final Pattern FOLIO = Pattern.compile("(?i)^\\s*ito-0*(\\d{1,9})\\s*$");

    private final StringBuilder where = new StringBuilder();
    private final Map<String, Object> parametros = new LinkedHashMap<>();

    /** Condición fija, sin parámetros. */
    public Consulta y(String condicion) {
        where.append(where.length() == 0 ? " WHERE " : " AND ").append(condicion);
        return this;
    }

    /** Condición con un parámetro que siempre se aplica. */
    public Consulta y(String condicion, String parametro, Object valor) {
        parametros.put(parametro, valor);
        return y(condicion);
    }

    /** Condición que solo se aplica si el valor existe (no null, no texto vacío, no colección vacía). */
    public Consulta siHay(String condicion, String parametro, Object valor) {
        return hay(valor) ? y(condicion, parametro, valor) : this;
    }

    /**
     * Restringe a un conjunto de carreras: null = sin restricción (jefatura ve todas);
     * vacío = ninguna fila (coordinación sin carreras asignadas no ve nada).
     */
    public Consulta dentroDe(String ruta, Collection<?> valores) {
        if (valores == null) {
            return this;
        }
        if (valores.isEmpty()) {
            return y("1 = 0");
        }
        String nombre = "p" + parametros.size();
        return y(ruta + " IN :" + nombre, nombre, valores);
    }

    /**
     * Búsqueda libre: «ITO-0042» filtra por folio exacto (si se da la ruta del folio);
     * cualquier otro texto busca sin distinguir mayúsculas en las rutas indicadas.
     */
    public Consulta texto(String texto, String rutaFolio, String... rutas) {
        if (texto == null || texto.trim().isEmpty()) {
            return this;
        }
        Matcher m = FOLIO.matcher(texto);
        if (rutaFolio != null && m.matches()) {
            return y(rutaFolio + " = :folio", "folio", Integer.valueOf(m.group(1)));
        }
        StringBuilder o = new StringBuilder("(");
        for (int i = 0; i < rutas.length; i++) {
            o.append(i == 0 ? "" : " OR ").append("LOWER(").append(rutas[i]).append(") LIKE :texto");
        }
        return y(o.append(')').toString(), "texto", "%" + texto.trim().toLowerCase() + "%");
    }

    /** Página de resultados. */
    public <T> List<T> lista(EntityManager em, String seleccion, String orden, Class<T> tipo, int inicio, int maximo) {
        TypedQuery<T> q = crear(em, seleccion + where + (orden == null ? "" : " " + orden), tipo);
        if (maximo > 0) {
            q.setFirstResult(inicio).setMaxResults(maximo);
        }
        return q.getResultList();
    }

    /** Todos los resultados (consultas agregadas: GROUP BY va en {@code final}). */
    public <T> List<T> lista(EntityManager em, String seleccion, String finalConsulta, Class<T> tipo) {
        return lista(em, seleccion, finalConsulta, tipo, 0, 0);
    }

    /** Total con los mismos filtros. */
    public long contar(EntityManager em, String conteo) {
        return crear(em, conteo + where, Long.class).getSingleResult();
    }

    private <T> TypedQuery<T> crear(EntityManager em, String jpql, Class<T> tipo) {
        TypedQuery<T> q = em.createQuery(jpql, tipo);
        for (Map.Entry<String, Object> p : parametros.entrySet()) {
            q.setParameter(p.getKey(), p.getValue());
        }
        return q;
    }

    private static boolean hay(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof String) {
            return !((String) v).trim().isEmpty();
        }
        return !(v instanceof Collection) || !((Collection<?>) v).isEmpty();
    }
}
