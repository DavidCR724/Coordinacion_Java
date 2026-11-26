package modelo.bean;

import acceso.datos.NotificacionRepository;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import modelo.entity.Fechas;
import modelo.entity.Notificacion;
import modelo.entity.Solicitud;
import modelo.entity.Usuario;

/**
 * «servicio» Notificaciones (HU 5.1, y la base de 5.2).
 * avisar() guarda la notificación dentro de la transacción de quien la provoca: si esa operación
 * se deshace, la notificación también. El correo lo manda después CorreoService.
 */
@Stateless
public class NotificacionService {

    @EJB
    private NotificacionRepository notificaciones;

    @EJB
    private Guardia guardia;

    public Notificacion avisar(Usuario destinatario, Solicitud solicitud, String titulo, String mensaje) {
        Notificacion n = new Notificacion(destinatario, solicitud, titulo, mensaje);
        notificaciones.guardar(n);
        return n;
    }

    /** El mismo aviso para varias personas (por ejemplo, todos los interesados de una apertura de grupo). */
    public void avisarATodos(Collection<Usuario> destinatarios, Solicitud solicitud, String titulo, String mensaje) {
        for (Usuario u : destinatarios) {
            avisar(u, solicitud, titulo, mensaje);
        }
    }

    public List<Notificacion> recientes(UUID idUsuario, int maximo) {
        return notificaciones.de(guardia.usuario(idUsuario), maximo);
    }

    public long sinLeer(UUID idUsuario) {
        return notificaciones.sinLeer(guardia.usuario(idUsuario));
    }

    public void marcarLeidas(UUID idUsuario) {
        for (Notificacion n : notificaciones.de(guardia.usuario(idUsuario), 500)) {
            if (n.getLeidaEn() == null) {
                n.setLeidaEn(Fechas.ahora());
            }
        }
    }
}
