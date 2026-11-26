package modelo.bean;

/** HU 2.9: el alumno ya tiene una solicitud abierta de ese trámite. Lleva el folio existente. */
public class SolicitudDuplicadaException extends ReglaNegocioException {

    private static final long serialVersionUID = 1L;

    private final Integer folio;

    public SolicitudDuplicadaException(Integer folio) {
        super("Ya tienes una solicitud abierta de este trámite"
                + (folio == null ? "." : String.format(": ITO-%04d.", folio)));
        this.folio = folio;
    }

    public Integer getFolio() { return folio; }
}
