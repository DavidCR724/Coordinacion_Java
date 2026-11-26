package modelo.bean;

/**
 * Datos de la petición web que el EJB no puede ver por sí mismo (la IP de origen, HU 8.2).
 * El filtro del WAR la fija al empezar cada petición y la borra al terminar; como el EJB se
 * llama de forma local, corre en el mismo hilo y la encuentra aquí.
 */
public final class ContextoPeticion {

    private static final ThreadLocal<String> IP = new ThreadLocal<>();

    private ContextoPeticion() { }

    public static void iniciar(String ip) {
        IP.set(ip);
    }

    public static void terminar() {
        IP.remove();
    }

    /** IP de quien hace la petición, o null si la llamada no vino de la web (tareas programadas). */
    public static String ip() {
        return IP.get();
    }
}
