package cl.duoc.pedidos360.mensajeriaadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros propios del microservicio administrador: donde esta la API de gestion de
 * RabbitMQ y con que credenciales se consulta.
 *
 * <p>Se configuran bajo el prefijo {@code pedidos360.rabbit-admin}. Los nombres de la
 * topologia del sistema no viven aqui: esos estan en {@code pedidos360.mensajeria}.
 */
@ConfigurationProperties(prefix = "pedidos360.rabbit-admin")
public class AdminMensajeriaProperties {

  /** URL base del plugin de gestion de RabbitMQ. */
  private String urlGestion = "http://localhost:15672";

  /** Usuario con permiso de lectura sobre la API de gestion. */
  private String usuario = "pedidos360";

  /** Clave del usuario de gestion. */
  private String clave = "pedidos360";

  /** Virtual host sobre el que trabaja el administrador. */
  private String virtualHost = "/";

  /** Segundos de espera al consultar la API de gestion. */
  private int timeoutSegundos = 5;

  /**
   * Si es true, las colas y exchanges que forman la topologia del sistema no se pueden
   * eliminar desde el API. Evita dejar la aplicacion sin mensajeria por un clic.
   */
  private boolean protegerTopologiaDelSistema = true;

  public String getUrlGestion() {
    return urlGestion;
  }

  public void setUrlGestion(String urlGestion) {
    this.urlGestion = urlGestion;
  }

  public String getUsuario() {
    return usuario;
  }

  public void setUsuario(String usuario) {
    this.usuario = usuario;
  }

  public String getClave() {
    return clave;
  }

  public void setClave(String clave) {
    this.clave = clave;
  }

  public String getVirtualHost() {
    return virtualHost;
  }

  public void setVirtualHost(String virtualHost) {
    this.virtualHost = virtualHost;
  }

  public int getTimeoutSegundos() {
    return timeoutSegundos;
  }

  public void setTimeoutSegundos(int timeoutSegundos) {
    this.timeoutSegundos = timeoutSegundos;
  }

  public boolean isProtegerTopologiaDelSistema() {
    return protegerTopologiaDelSistema;
  }

  public void setProtegerTopologiaDelSistema(boolean protegerTopologiaDelSistema) {
    this.protegerTopologiaDelSistema = protegerTopologiaDelSistema;
  }
}
