package modelo.bean;

import java.util.EnumSet;
import java.util.Set;
import modelo.entity.Rol;

/**
 * Qué puede hacer cada rol (patrón Policy). Es la ÚNICA tabla de permisos del sistema:
 * los servicios piden un permiso a {@link Guardia} en vez de revisar roles por su cuenta.
 *
 * <pre>
 *                      ESTUDIANTE  COORDINADOR  JEFE
 * SOLICITAR                 x
 * ATENDER                               x          x
 * AGENDAR_ATENCION                      x
 * SEGUIMIENTO                                      x
 * GESTIONAR_CARRERA                     x*         x     (* solo sobre sus carreras: Guardia.alcance)
 * CONFIGURAR                                       x
 * </pre>
 *
 * Ya no hay rol de administración: lo que hacía se repartió según su alcance. Lo que pertenece a
 * una carrera (personal, padrón, auditoría) lo gestiona su coordinación y, en todas, jefatura.
 * Lo que es común a todas las carreras (catálogo de trámites, periodos y prioridades) solo lo
 * cambia jefatura: un cambio ahí afectaría a carreras ajenas.
 */
public enum Permiso {

    /** Registrar trámites, pedir o adherirse a grupos, agendar citas (HU 2.2, 4.1, 4.2, 9.2). */
    SOLICITAR("Esta acción es solo para estudiantes.", Rol.ESTUDIANTE),
    /** Bandeja, atención, avisos, corrección de alumnos (HU 2.x, 3.x, 5.3, 6.5). */
    ATENDER("Solo coordinación o jefatura pueden hacer esto.", Rol.COORDINADOR, Rol.JEFE),
    /** Publicar horarios de atención y llevar la agenda del día (HU 9.1, 9.3). */
    AGENDAR_ATENCION("Solo coordinación publica horarios de atención.", Rol.COORDINADOR),
    /** Panel de seguimiento y exportación (HU 7.x). */
    SEGUIMIENTO("El panel de seguimiento es de jefatura.", Rol.JEFE),
    /** Personal, padrón y auditoría de la propia carrera; jefatura en todas (HU 6.3, 8.1, 8.2). */
    GESTIONAR_CARRERA("Solo coordinación o jefatura pueden hacer esto.", Rol.COORDINADOR, Rol.JEFE),
    /** Catálogo, periodos y prioridades: comunes a todas las carreras (HU 1.3, 3.2, 8.3). */
    CONFIGURAR("Esta configuración es común a todas las carreras: la hace jefatura.", Rol.JEFE);

    private final String mensajeNegado;
    private final Set<Rol> roles;

    Permiso(String mensajeNegado, Rol primero, Rol... resto) {
        this.mensajeNegado = mensajeNegado;
        this.roles = EnumSet.of(primero, resto);
    }

    public boolean permite(Rol rol) {
        return roles.contains(rol);
    }

    public String getMensajeNegado() {
        return mensajeNegado;
    }
}
