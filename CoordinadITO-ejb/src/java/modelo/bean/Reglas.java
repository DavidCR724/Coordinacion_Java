package modelo.bean;

import java.time.LocalDate;
import modelo.entity.Tramite;

/**
 * Reglas de validación compartidas por todos los servicios. Cada regla está escrita una vez;
 * los mensajes siguen el mismo estilo («Escribe el motivo.», «El título: máximo 150 caracteres.»).
 */
public final class Reglas {

    /** Largo máximo de los textos libres (constancias, mensajes, motivos). */
    public static final int MAX_TEXTO = 2000;

    private Reglas() { }

    /** Texto sin espacios sobrantes, o null si quedó vacío. */
    public static String limpio(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    /** Texto obligatorio de hasta {@link #MAX_TEXTO} caracteres. */
    public static String texto(String valor, String nombre) {
        return texto(valor, nombre, MAX_TEXTO);
    }

    public static String texto(String valor, String nombre, int maximo) {
        String v = limpio(valor);
        if (v == null) {
            throw new ReglaNegocioException("Escribe " + nombre.toLowerCase() + ".");
        }
        return opcional(v, nombre, maximo);
    }

    /** Texto opcional: null si viene vacío; si trae algo, respeta el máximo. */
    public static String opcional(String valor, String nombre) {
        return opcional(valor, nombre, MAX_TEXTO);
    }

    public static String opcional(String valor, String nombre, int maximo) {
        String v = limpio(valor);
        if (v != null && v.length() > maximo) {
            throw new ReglaNegocioException(nombre + ": máximo " + maximo + " caracteres.");
        }
        return v;
    }

    /** Un valor que no puede faltar (selección de una lista, fecha, etc.). */
    public static <T> T requerido(T valor, String mensaje) {
        if (valor == null) {
            throw new ReglaNegocioException(mensaje);
        }
        return valor;
    }

    /** Periodo cerrado [inicio, fin]; cualquiera de los dos puede faltar. */
    public static void periodo(LocalDate inicio, LocalDate fin) {
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new ReglaNegocioException("La fecha final no puede ser anterior a la inicial.");
        }
    }

    /** Semestre escolar válido (1 a 14, igual que la restricción de la base). */
    public static short semestre(Integer semestre) {
        if (semestre == null || semestre < 1 || semestre > 14) {
            throw new ReglaNegocioException("El semestre debe estar entre 1 y 14.");
        }
        return semestre.shortValue();
    }

    /** HU 1.4: el trámite recibe solicitudes hoy. */
    public static void disponible(Tramite tramite) {
        switch (tramite.getDisponibilidad()) {
            case DISPONIBLE:
                return;
            case AUN_NO_ABRE:
                throw new ReglaNegocioException("Este trámite abre el " + tramite.getInicioPeriodo() + ".");
            case PERIODO_CONCLUIDO:
                throw new ReglaNegocioException("El periodo de este trámite cerró el " + tramite.getFinPeriodo() + ".");
            default:
                throw new ReglaNegocioException("Este trámite no está disponible.");
        }
    }

    /** ¿La excepción (o alguna de sus causas) viene de la restricción con este nombre? */
    public static boolean violaRestriccion(Throwable t, String restriccion) {
        String buscado = restriccion.toLowerCase();
        for (Throwable x = t; x != null; x = x.getCause() == x ? null : x.getCause()) {
            if (x.getMessage() != null && x.getMessage().toLowerCase().contains(buscado)) {
                return true;
            }
        }
        return false;
    }
}
