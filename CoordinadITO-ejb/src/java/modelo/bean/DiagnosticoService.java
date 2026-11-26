package modelo.bean;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * «servicio» Prueba la conexión con la base de datos a través de JPA y revisa que cada modelo
 * (@Entity) cuadre con las tablas reales (ver {@link #revisarModelos()}).
 *
 * Es el primer EJB del módulo: con él el EAR ya despliega (antes fallaba con
 * "contains zero ejb"). No escribe nada; solo lee el catálogo de PostgreSQL.
 */
@Stateless
public class DiagnosticoService {

    private static final Logger LOG = Logger.getLogger(DiagnosticoService.class.getName());

    @PersistenceContext(unitName = "CoordinadITOPU")
    private EntityManager em;

    /**
     * Sin transacción: si la base no responde, el error se reporta en pantalla
     * en lugar de convertirse en una excepción del contenedor.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public EstadoConexion probar() {
        long inicio = System.nanoTime();
        try {
            Object[] fila = (Object[]) em.createNativeQuery(
                    "SELECT current_database(), current_user, "
                    + "coalesce(inet_server_addr()::text, 'pooler'), "
                    + "split_part(version(), ',', 1), to_char(now(), 'YYYY-MM-DD HH24:MI:SS TZ')")
                    .getSingleResult();

            @SuppressWarnings("unchecked")
            List<Object> nombres = em.createNativeQuery(
                    "SELECT upper(table_name) FROM information_schema.tables "
                    + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY 1")
                    .getResultList();
            List<String> tablas = new ArrayList<>();
            for (Object n : nombres) {
                tablas.add(String.valueOf(n));
            }

            return EstadoConexion.exito(texto(fila[0]), texto(fila[1]), texto(fila[2]), texto(fila[3]),
                    texto(fila[4]), milis(inicio), tablas);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo consultar la base de datos", e);
            return EstadoConexion.falla(causaRaiz(e), milis(inicio));
        }
    }

    /** Cuántas filas lee como máximo la prueba de lectura de cada modelo. */
    private static final int MUESTRA = 200;

    /**
     * Revisa cada modelo (@Entity) contra la base a la que apunta jdbc/coordinadito:
     *  1. que exista su tabla y cada columna que mapea (@Column, @JoinColumn, @JoinTable);
     *  2. que los valores guardados en columnas de enum (@Enumerated STRING) existan en el enum de Java
     *     (por ejemplo, un ROL que Java ya no conoce rompería cualquier lectura de USUARIO);
     *  3. que JPA cuente y lea sus filas ({@value #MUESTRA} como máximo) sin error.
     * Solo usa la API estándar de JPA y reflexión: no depende de EclipseLink. No escribe nada.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<RevisionModelo> revisarModelos() {
        Map<String, Set<String>> columnas = columnasDeLaBase();
        Map<String, Set<String>> obligatorias = obligatoriasSinDefault();
        List<RevisionModelo> r = new ArrayList<>();
        List<EntityType<?>> tipos = new ArrayList<>(em.getMetamodel().getEntities());
        tipos.sort(Comparator.comparing(EntityType::getName));
        for (EntityType<?> tipo : tipos) {
            Class<?> clase = tipo.getJavaType();
            Table t = clase.getAnnotation(Table.class);
            String tabla = (t == null || t.name().isEmpty() ? tipo.getName() : t.name()).toUpperCase();
            RevisionModelo rev = new RevisionModelo(tipo.getName(), tabla);
            Set<String> deLaTabla = columnas.get(tabla);
            rev.tablaExiste(deLaTabla != null);
            if (deLaTabla == null) {
                rev.falta("Falta la tabla " + tabla);
            } else {
                Set<String> mapeadas = revisarCampos(clase, tabla, deLaTabla, columnas, rev);
                // Al revés: una columna NOT NULL sin DEFAULT que el modelo no mapea hace fallar cada INSERT.
                for (String c : obligatorias.getOrDefault(tabla, Collections.<String>emptySet())) {
                    if (!mapeadas.contains(c)) {
                        rev.falta(tabla + "." + c + " es obligatoria y el modelo no la llena: fallarían las altas");
                    }
                }
            }
            if (rev.getFaltantes().isEmpty()) {
                try {
                    long filas = em.createQuery("SELECT COUNT(e) FROM " + tipo.getName() + " e", Long.class).getSingleResult();
                    int leidas = em.createQuery("SELECT e FROM " + tipo.getName() + " e").setMaxResults(MUESTRA).getResultList().size();
                    rev.lectura(filas, leidas);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "No se pudo leer el modelo " + tipo.getName(), e);
                    rev.error(resumen(causaRaiz(e)));
                }
            }
            r.add(rev);
        }
        return r;
    }

    /**
     * Columnas por campo, subiendo por las superclases. Se salta lo que no vive en esta tabla.
     * Devuelve las columnas de la tabla principal que el modelo mapea.
     */
    private Set<String> revisarCampos(Class<?> clase, String tabla, Set<String> deLaTabla,
                                      Map<String, Set<String>> columnas, RevisionModelo rev) {
        Set<String> mapeadas = new HashSet<>();
        for (Class<?> c = clase; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isAnnotationPresent(Transient.class)
                        || f.isAnnotationPresent(OneToMany.class)) {
                    continue;   // sin columna propia (las listas @OneToMany viven en la otra tabla)
                }
                ManyToMany mm = f.getAnnotation(ManyToMany.class);
                if (mm != null) {
                    JoinTable jt = f.getAnnotation(JoinTable.class);
                    if (jt == null) {
                        continue;   // lado inverso (mappedBy): la relación la revisa el otro modelo
                    }
                    String rel = jt.name().toUpperCase();
                    rev.relacion(rel);
                    Set<String> deRel = columnas.get(rel);
                    if (deRel == null) {
                        rev.falta("Falta la tabla de relación " + rel);
                        continue;
                    }
                    for (JoinColumn jc : jt.joinColumns()) {
                        exigir(rel, jc.name(), deRel, rev);
                    }
                    for (JoinColumn jc : jt.inverseJoinColumns()) {
                        exigir(rel, jc.name(), deRel, rev);
                    }
                    continue;
                }
                JoinColumn jc = f.getAnnotation(JoinColumn.class);
                Column col = f.getAnnotation(Column.class);
                String nombre = jc != null ? jc.name() : (col != null && !col.name().isEmpty() ? col.name() : f.getName());
                mapeadas.add(nombre.toUpperCase());
                exigir(tabla, nombre, deLaTabla, rev);
                Enumerated en = f.getAnnotation(Enumerated.class);
                if (en != null && en.value() == EnumType.STRING && f.getType().isEnum() && deLaTabla.contains(nombre.toUpperCase())) {
                    revisarEnum(tabla, nombre, f.getType(), rev);
                }
            }
        }
        return mapeadas;
    }

    private static void exigir(String tabla, String columna, Set<String> existentes, RevisionModelo rev) {
        rev.columnaRevisada();
        if (!existentes.contains(columna.toUpperCase())) {
            rev.falta("Falta la columna " + tabla + "." + columna.toUpperCase());
        }
    }

    /** Valores guardados en la columna que el enum de Java no conoce. */
    private void revisarEnum(String tabla, String columna, Class<?> tipoEnum, RevisionModelo rev) {
        Set<String> validos = new HashSet<>();
        for (Object constante : tipoEnum.getEnumConstants()) {
            validos.add(((Enum<?>) constante).name());
        }
        @SuppressWarnings("unchecked")
        List<Object> enLaBase = em.createNativeQuery("SELECT DISTINCT " + columna + "::text FROM " + tabla
                + " WHERE " + columna + " IS NOT NULL").getResultList();
        for (Object v : enLaBase) {
            if (!validos.contains(String.valueOf(v))) {
                rev.valorInvalido(tabla + "." + columna.toUpperCase() + " = '" + v + "' (" + tipoEnum.getSimpleName() + " no lo tiene)");
            }
        }
    }

    /** Los mensajes de EclipseLink traen el descriptor completo: basta con la primera parte. */
    private static String resumen(String error) {
        int corte = error.indexOf("Mapping:");
        String r = (corte > 0 ? error.substring(0, corte) : error).replace("Exception Description: ", "")
                .replaceAll("\\s+", " ").trim();
        return r.length() > 220 ? r.substring(0, 220) + "…" : r;
    }

    /** TABLA → columnas NOT NULL sin DEFAULT ni identidad: las que todo INSERT debe llenar. */
    private Map<String, Set<String>> obligatoriasSinDefault() {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery("SELECT upper(table_name), upper(column_name) FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND is_nullable = 'NO' AND column_default IS NULL "
                + "AND is_identity = 'NO' AND is_generated = 'NEVER'").getResultList();
        Map<String, Set<String>> r = new HashMap<>();
        for (Object[] f : filas) {
            r.computeIfAbsent(String.valueOf(f[0]), k -> new HashSet<>()).add(String.valueOf(f[1]));
        }
        return r;
    }

    /** TABLA → sus COLUMNAS (en mayúsculas), del esquema public. */
    private Map<String, Set<String>> columnasDeLaBase() {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery("SELECT upper(c.table_name), upper(c.column_name) "
                + "FROM information_schema.columns c JOIN information_schema.tables t "
                + "ON t.table_schema = c.table_schema AND t.table_name = c.table_name "
                + "WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE'").getResultList();
        Map<String, Set<String>> r = new HashMap<>();
        for (Object[] f : filas) {
            r.computeIfAbsent(String.valueOf(f[0]), k -> new HashSet<>()).add(String.valueOf(f[1]));
        }
        return r;
    }

    private static String texto(Object o) {
        return o == null ? "" : o.toString();
    }

    private static long milis(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000L;
    }

    /** El mensaje útil suele estar en la excepción más profunda (la del driver de PostgreSQL). */
    private static String causaRaiz(Throwable t) {
        Throwable raiz = t;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String msg = raiz.getMessage();
        return raiz.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }
}
