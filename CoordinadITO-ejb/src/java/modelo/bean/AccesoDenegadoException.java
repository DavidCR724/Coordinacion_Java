package modelo.bean;

/** El usuario no tiene permiso para esa operación o ese registro. Se valida en el servidor, no solo en el menú. */
public class AccesoDenegadoException extends ReglaNegocioException {

    private static final long serialVersionUID = 1L;

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
