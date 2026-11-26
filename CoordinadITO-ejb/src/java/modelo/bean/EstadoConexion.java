package modelo.bean;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Resultado de probar la conexión con la base de datos.
 * Es un objeto de solo lectura que el servicio arma y la vista muestra.
 */
public class EstadoConexion implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean conectado;
    private final String baseDatos;
    private final String usuario;
    private final String servidor;
    private final String version;
    private final String horaServidor;
    private final long milisegundos;
    private final List<String> tablas;
    private final String error;

    private EstadoConexion(boolean conectado, String baseDatos, String usuario, String servidor, String version,
                           String horaServidor, long milisegundos, List<String> tablas, String error) {
        this.conectado = conectado;
        this.baseDatos = baseDatos;
        this.usuario = usuario;
        this.servidor = servidor;
        this.version = version;
        this.horaServidor = horaServidor;
        this.milisegundos = milisegundos;
        this.tablas = tablas == null ? Collections.<String>emptyList() : Collections.unmodifiableList(tablas);
        this.error = error;
    }

    public static EstadoConexion exito(String baseDatos, String usuario, String servidor, String version,
                                       String horaServidor, long milisegundos, List<String> tablas) {
        return new EstadoConexion(true, baseDatos, usuario, servidor, version, horaServidor, milisegundos, tablas, null);
    }

    public static EstadoConexion falla(String error, long milisegundos) {
        return new EstadoConexion(false, null, null, null, null, null, milisegundos, null, error);
    }

    public boolean isConectado() { return conectado; }
    public String getBaseDatos() { return baseDatos; }
    public String getUsuario() { return usuario; }
    public String getServidor() { return servidor; }
    public String getVersion() { return version; }
    public String getHoraServidor() { return horaServidor; }
    public long getMilisegundos() { return milisegundos; }
    public List<String> getTablas() { return tablas; }
    public int getTotalTablas() { return tablas.size(); }
    public String getError() { return error; }
}
