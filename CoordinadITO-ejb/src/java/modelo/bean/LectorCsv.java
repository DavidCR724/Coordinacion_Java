package modelo.bean;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lector de CSV tolerante con lo que exporta Excel: BOM UTF-8, separador «,» o «;» (se detecta
 * en el encabezado), comillas dobles y saltos de línea dentro de comillas.
 * Los encabezados se normalizan (minúsculas, sin acentos, espacios → _) para comparar nombres de columna.
 */
public final class LectorCsv {

    private final List<String> encabezados;
    private final List<List<String>> filas;

    private LectorCsv(List<String> encabezados, List<List<String>> filas) {
        this.encabezados = encabezados;
        this.filas = filas;
    }

    public static LectorCsv leer(byte[] contenido) {
        String texto = new String(contenido, StandardCharsets.UTF_8);
        if (texto.startsWith("﻿")) {
            texto = texto.substring(1);
        }
        int finLinea = texto.indexOf('\n');
        String primera = finLinea < 0 ? texto : texto.substring(0, finLinea);
        char separador = primera.chars().filter(c -> c == ';').count() > primera.chars().filter(c -> c == ',').count() ? ';' : ',';
        List<List<String>> todas = partir(texto, separador);
        if (todas.isEmpty()) {
            return new LectorCsv(new ArrayList<String>(), new ArrayList<List<String>>());
        }
        List<String> enc = new ArrayList<>();
        for (String e : todas.get(0)) {
            enc.add(normalizar(e));
        }
        return new LectorCsv(enc, todas.subList(1, todas.size()));
    }

    /** Columnas que faltan entre las requeridas (vacío = el formato es correcto). */
    public List<String> faltantes(String... requeridas) {
        List<String> r = new ArrayList<>();
        for (String c : requeridas) {
            if (!encabezados.contains(c)) {
                r.add(c);
            }
        }
        return r;
    }

    /** Cada fila como mapa columna → valor (celdas faltantes = cadena vacía). */
    public List<Map<String, String>> registros() {
        List<Map<String, String>> r = new ArrayList<>();
        for (List<String> f : filas) {
            Map<String, String> m = new HashMap<>();
            for (int i = 0; i < encabezados.size(); i++) {
                m.put(encabezados.get(i), i < f.size() ? f.get(i).trim() : "");
            }
            r.add(m);
        }
        return r;
    }

    static String normalizar(String s) {
        String sinAcentos = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[\\s.-]+", "_");
    }

    /** Parte el texto en filas y celdas respetando comillas. Omite filas totalmente vacías. */
    private static List<List<String>> partir(String texto, char sep) {
        List<List<String>> filas = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (comillas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    celda.append('"');
                    i++;
                } else if (c == '"') {
                    comillas = false;
                } else {
                    celda.append(c);
                }
            } else if (c == '"') {
                comillas = true;
            } else if (c == sep) {
                fila.add(celda.toString());
                celda.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                cerrarFila(filas, fila, celda);
                fila = new ArrayList<>();
            } else {
                celda.append(c);
            }
        }
        cerrarFila(filas, fila, celda);
        return filas;
    }

    private static void cerrarFila(List<List<String>> filas, List<String> fila, StringBuilder celda) {
        fila.add(celda.toString());
        celda.setLength(0);
        for (String x : fila) {
            if (!x.trim().isEmpty()) {
                filas.add(fila);
                return;
            }
        }
    }
}
