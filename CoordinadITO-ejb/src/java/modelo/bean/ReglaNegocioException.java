package modelo.bean;

import jakarta.ejb.ApplicationException;

/**
 * Se lanza cuando una operación viola una regla de negocio (periodo cerrado, campo obligatorio vacío,
 * transición no permitida...). El mensaje está escrito para mostrarse tal cual al usuario.
 * rollback = true: la transacción se deshace y no queda nada a medias. Las subclases heredan esto.
 */
@ApplicationException(rollback = true, inherited = true)
public class ReglaNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
