package modelo.bean;

/** El registro pedido no existe (folio, trámite o usuario). */
public class NoEncontradoException extends ReglaNegocioException {

    private static final long serialVersionUID = 1L;

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
