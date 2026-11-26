package modelo.bean;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import modelo.entity.CargaPadron;

/** Resultado de procesar un archivo de padrón: los totales y el detalle de las filas rechazadas. */
public class ResultadoCarga implements Serializable {

    private static final long serialVersionUID = 1L;

    private final CargaPadron carga;
    private final List<String> errores;
    private final int retirados;

    public ResultadoCarga(CargaPadron carga, List<String> errores, int retirados) {
        this.carga = carga;
        this.errores = Collections.unmodifiableList(errores);
        this.retirados = retirados;
    }

    public CargaPadron getCarga() { return carga; }
    /** «Fila 7: la carrera XYZ no existe.» (máximo 50). */
    public List<String> getErrores() { return errores; }
    /** Alumnos que estaban vigentes y no vienen en este archivo. */
    public int getRetirados() { return retirados; }
}
