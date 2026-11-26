package modelo.entity;

/** Rol del usuario. Coincide con el tipo ENUM ROLES de la base (el valor ADMINISTRADOR ya no se usa). */
public enum Rol {
    ESTUDIANTE("Estudiante"),
    COORDINADOR("Coordinación"),
    JEFE("Jefatura");
    // ADMINISTRADOR se retiró (bd/06): sus tareas son de coordinación (su carrera) o de jefatura.

    private final String etiqueta;

    Rol(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
