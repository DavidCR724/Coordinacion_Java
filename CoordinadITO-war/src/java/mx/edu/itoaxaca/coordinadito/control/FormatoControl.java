package mx.edu.itoaxaca.coordinadito.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.time.OffsetDateTime;
import mx.edu.itoaxaca.coordinadito.control.vista.Formato;

/** #{formato.fecha(x)} y #{formato.fechaHora(x)}: el mismo formato de {@link Formato} desde EL. */
@Named("formato")
@ApplicationScoped
public class FormatoControl {

    public String fecha(OffsetDateTime t) { return Formato.fecha(t); }
    public String fechaHora(OffsetDateTime t) { return Formato.fechaHora(t); }
}
