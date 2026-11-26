package modelo.bean;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Escritor de CSV declarativo (patrón Builder): se describen las columnas una vez y se generan
 * los bytes para cualquier lista. Incluye BOM UTF-8 para que Excel muestre bien los acentos.
 *
 * <pre>
 * byte[] archivo = new Csv&lt;Solicitud&gt;()
 *         .columna("Folio", Solicitud::getFolioTexto)
 *         .columna("Estado", s -&gt; s.getEstado().getEtiqueta())
 *         .escribir(lista);
 * </pre>
 */
public final class Csv<T> {

    private final List<String> titulos = new ArrayList<>();
    private final List<Function<T, Object>> valores = new ArrayList<>();

    public Csv<T> columna(String titulo, Function<T, Object> valor) {
        titulos.add(titulo);
        valores.add(valor);
        return this;
    }

    public byte[] escribir(List<T> filas) {
        StringBuilder sb = new StringBuilder();
        linea(sb, new ArrayList<Object>(titulos));
        for (T fila : filas) {
            List<Object> celdas = new ArrayList<>();
            for (Function<T, Object> v : valores) {
                celdas.add(v.apply(fila));
            }
            linea(sb, celdas);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);
        byte[] texto = sb.toString().getBytes(StandardCharsets.UTF_8);
        out.write(texto, 0, texto.length);
        return out.toByteArray();
    }

    private static void linea(StringBuilder sb, List<Object> celdas) {
        for (int i = 0; i < celdas.size(); i++) {
            Object c = celdas.get(i);
            String s = c == null ? "" : c.toString();
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                s = '"' + s.replace("\"", "\"\"") + '"';
            }
            sb.append(i == 0 ? "" : ",").append(s);
        }
        sb.append("\r\n");
    }
}
