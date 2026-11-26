package modelo.bean;

/**
 * HU 4.2: ya hay una solicitud de apertura activa para esa materia.
 * La vista la usa para ofrecer «Adherirme» en lugar de crear otra.
 */
public class GrupoExistenteException extends ReglaNegocioException {

    private static final long serialVersionUID = 1L;

    private final Integer folio;

    public GrupoExistenteException(Integer folio) {
        super("Ya existe una solicitud de apertura para esa materia. Adhiérete a ella para sumar tu interés.");
        this.folio = folio;
    }

    public Integer getFolio() {
        return folio;
    }
}
