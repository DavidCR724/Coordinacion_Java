package modelo.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado de revisar un modelo (@Entity) contra la base real: si su tabla y sus columnas
 * existen, si los valores de sus enums son válidos y si JPA puede leer sus filas.
 * Lo arma {@link DiagnosticoService#revisarModelos()}; lo muestra vista/diagnostico.xhtml.
 */
public class RevisionModelo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String modelo;
    private final String tabla;
    private boolean tablaExiste;
    private int columnasRevisadas;
    private final List<String> faltantes = new ArrayList<>();
    /** Tablas de relación (@JoinTable) que también usa este modelo. */
    private final List<String> relaciones = new ArrayList<>();
    private final List<String> valoresInvalidos = new ArrayList<>();
    private long filas = -1;
    private int leidas = -1;
    private String error;

    RevisionModelo(String modelo, String tabla) {
        this.modelo = modelo;
        this.tabla = tabla;
    }

    void tablaExiste(boolean existe) { this.tablaExiste = existe; }
    void columnaRevisada() { columnasRevisadas++; }
    void falta(String que) { faltantes.add(que); }
    void relacion(String tablaRelacion) { relaciones.add(tablaRelacion); }
    void valorInvalido(String que) { valoresInvalidos.add(que); }
    void lectura(long filas, int leidas) { this.filas = filas; this.leidas = leidas; }
    void error(String error) { this.error = error; }

    /** Todo cuadra: tabla, columnas, valores y lectura. */
    public boolean isCorrecto() {
        return tablaExiste && faltantes.isEmpty() && valoresInvalidos.isEmpty() && error == null;
    }

    public String getModelo() { return modelo; }
    public String getTabla() { return tabla; }
    public boolean isTablaExiste() { return tablaExiste; }
    public int getColumnasRevisadas() { return columnasRevisadas; }
    public List<String> getRelaciones() { return Collections.unmodifiableList(relaciones); }
    public List<String> getFaltantes() { return Collections.unmodifiableList(faltantes); }
    public List<String> getValoresInvalidos() { return Collections.unmodifiableList(valoresInvalidos); }
    public long getFilas() { return filas; }
    public int getLeidas() { return leidas; }
    public String getError() { return error; }
}
