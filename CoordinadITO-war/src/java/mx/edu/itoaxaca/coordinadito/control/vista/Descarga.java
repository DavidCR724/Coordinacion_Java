package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.ByteArrayInputStream;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/** Un archivo para p:fileDownload a partir de los bytes que entrega el EJB (null → no hay descarga). */
public final class Descarga {

    public static final String CSV = "text/csv; charset=UTF-8";

    private Descarga() { }

    public static StreamedContent de(String nombre, String tipo, byte[] contenido) {
        if (contenido == null) {
            return null;
        }
        return DefaultStreamedContent.builder()
                .name(nombre)
                .contentType(tipo)
                .contentLength((long) contenido.length)
                .stream(() -> new ByteArrayInputStream(contenido))
                .build();
    }
}
