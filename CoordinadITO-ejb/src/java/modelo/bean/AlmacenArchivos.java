package modelo.bean;

import jakarta.ejb.Stateless;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Guarda el contenido de los documentos adjuntos (HU 1.5) y devuelve la referencia que va a
 * DOCUMENTO_ADJUNTO.REFERENCIA.
 *
 * Versión actual: disco local, en la carpeta indicada por la propiedad del sistema
 * coordinadito.archivos (por defecto ~/coordinadito-archivos). Referencia: "local:&lt;nombre&gt;".
 *
 * LIMITACIÓN: con dos PCs, un archivo subido en una no existe en la otra. Para compartirlos se
 * reemplaza esta clase por una que use Supabase Storage; el resto del back no cambia porque solo
 * conoce guardar() y leer().
 */
@Stateless
public class AlmacenArchivos {

    static final String PREFIJO = "local:";

    public String guardar(ArchivoNuevo archivo) {
        String limpio = archivo.getNombre() == null ? "archivo" : archivo.getNombre().replaceAll("[^A-Za-z0-9._-]", "_");
        String nombre = UUID.randomUUID() + "-" + limpio;
        try {
            Path dir = carpeta();
            Files.createDirectories(dir);
            Files.write(dir.resolve(nombre), archivo.getContenido());
            return PREFIJO + nombre;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo " + archivo.getNombre(), e);
        }
    }

    public byte[] leer(String referencia) {
        if (referencia == null || !referencia.startsWith(PREFIJO)) {
            throw new NoEncontradoException("El archivo no está disponible en este equipo.");
        }
        Path p = carpeta().resolve(referencia.substring(PREFIJO.length())).normalize();
        if (!p.startsWith(carpeta()) || !Files.exists(p)) {
            throw new NoEncontradoException("El archivo no está disponible en este equipo.");
        }
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path carpeta() {
        String base = System.getProperty("coordinadito.archivos",
                Paths.get(System.getProperty("user.home"), "coordinadito-archivos").toString());
        return Paths.get(base).toAbsolutePath().normalize();
    }
}
