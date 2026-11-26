package mx.edu.itoaxaca.coordinadito.control.vista;

import java.io.Serializable;
import java.util.List;
import modelo.entity.CampoFormulario;
import modelo.entity.TipoCampo;

/** Un campo del formulario de un trámite, como lo pinta comp-campo-formulario. */
public class CampoVista implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final String etiqueta;
    private final String ejemplo;
    private final String tipo;
    private final boolean obligatorio;
    private final int maximo;
    private final List<String> opciones;

    public CampoVista(CampoFormulario c) {
        nombre = c.getId().toString();
        etiqueta = c.getEtiqueta();
        ejemplo = c.getAyuda() == null ? "" : c.getAyuda();
        tipo = c.getTipo().name();
        obligatorio = c.isObligatorio();
        int defecto = c.getTipo() == TipoCampo.TEXTO_LARGO ? 2000 : 255;
        maximo = c.getLongitudMax() == null ? defecto : c.getLongitudMax();
        opciones = c.getListaOpciones();
    }

    /** Clave del valor capturado: el id del campo (así lo espera SolicitudService.registrar). */
    public String getNombre() { return nombre; }
    public String getEtiqueta() { return etiqueta; }
    public String getEjemplo() { return ejemplo; }
    /** TEXTO, TEXTO_LARGO, NUMERO, FECHA o SELECCION. */
    public String getTipo() { return tipo; }
    public boolean isObligatorio() { return obligatorio; }
    public int getMaximo() { return maximo; }
    public boolean isMultilinea() { return "TEXTO_LARGO".equals(tipo); }
    public List<String> getOpciones() { return opciones; }
}
