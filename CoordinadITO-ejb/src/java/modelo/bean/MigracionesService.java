package modelo.bean;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * «servicio» Pone la base al día al desplegar: aplica las migraciones que le falten.
 *
 * Por qué: el código y la base avanzan juntos, pero la base vive en cada PC (o en Supabase) y
 * es fácil olvidar correr un script. Cuando pasa, la aplicación falla en cosas como «no existe
 * la relación aviso_lectura». Este servicio revisa, al arrancar, si cada migración ya está
 * (por una marca: una tabla o columna que esa migración crea) y, si falta, la ejecuta.
 *
 * Reglas:
 *  - Solo migraciones posteriores a 04. Si la base no tiene ni el esquema 01–04 (base vacía u otra
 *    base), no toca nada: eso se hace con herramientas/configurar-local.ps1 o en Supabase.
 *  - En orden; si una falla, se detiene ahí (cada script trae su BEGIN/COMMIT: no queda a medias).
 *  - Los scripts son los mismos archivos de bd/ (build.xml los copia al EJB): una sola copia del SQL.
 *  - Se apaga con la propiedad del sistema coordinadito.bd.migrar=false.
 * Lo que hizo se ve en vista/diagnostico.
 */
@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class MigracionesService {

    private static final Logger LOG = Logger.getLogger(MigracionesService.class.getName());

    /** Una migración y la marca que dice si ya está: tabla (y columna, si no es nula). */
    private static final class Migracion {
        final String archivo;
        final String tabla;
        final String columna;

        Migracion(String archivo, String tabla, String columna) {
            this.archivo = archivo;
            this.tabla = tabla;
            this.columna = columna;
        }
    }

    /** En orden. Para agregar una: el script en bd/ y su marca aquí. */
    private static final List<Migracion> MIGRACIONES = Collections.unmodifiableList(java.util.Arrays.asList(
            new Migracion("05-lectura-avisos.sql", "aviso_lectura", null),
            new Migracion("06-sin-administracion.sql", "carga_padron", "id_responsable"),
            new Migracion("07-codigo-acceso.sql", "codigo_acceso", null)));

    @Resource(lookup = "jdbc/coordinadito")
    private DataSource baseDeDatos;

    private final List<String> aplicadas = new ArrayList<>();
    private final List<String> alDia = new ArrayList<>();
    private String problema;

    @PostConstruct
    void aplicarPendientes() {
        if ("false".equalsIgnoreCase(System.getProperty("coordinadito.bd.migrar"))) {
            problema = "Desactivadas (coordinadito.bd.migrar=false).";
            return;
        }
        try (Connection c = baseDeDatos.getConnection()) {
            c.setAutoCommit(true);   // cada script trae su propio BEGIN/COMMIT
            if (!existe(c, "usuario", "origen_datos")) {
                problema = "La base no tiene el esquema 01–04: ejecuta herramientas/configurar-local.ps1 (o los scripts en Supabase).";
                LOG.warning("Migraciones: " + problema);
                return;
            }
            for (Migracion m : MIGRACIONES) {
                if (existe(c, m.tabla, m.columna)) {
                    alDia.add(m.archivo);
                    continue;
                }
                LOG.info("Migraciones: aplicando " + m.archivo);
                try (Statement st = c.createStatement()) {
                    st.execute(leer(m.archivo));
                }
                aplicadas.add(m.archivo);
                LOG.info("Migraciones: " + m.archivo + " aplicada");
            }
        } catch (SQLException | IOException | RuntimeException e) {
            problema = "No se pudo poner al día la base: " + e.getMessage();
            LOG.log(Level.SEVERE, "Migraciones: " + problema, e);
        }
    }

    private static boolean existe(Connection c, String tabla, String columna) throws SQLException {
        String sql = columna == null
                ? "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?"
                : "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tabla);
            if (columna != null) {
                ps.setString(2, columna);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String leer(String archivo) throws IOException {
        try (InputStream in = MigracionesService.class.getResourceAsStream("/bd/" + archivo)) {
            if (in == null) {
                throw new IOException("El EJB no trae bd/" + archivo + " (revisa el -post-compile de build.xml).");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) {
                out.write(b, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Migraciones que se aplicaron en este arranque. */
    public List<String> getAplicadas() { return Collections.unmodifiableList(aplicadas); }
    /** Las que ya estaban. */
    public List<String> getAlDia() { return Collections.unmodifiableList(alDia); }
    /** null si todo bien. */
    public String getProblema() { return problema; }
}
