package cl.duoc.pedidos360.mensajeriaadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del administrador de Kafka.
 *
 * <p>Se configuran bajo el prefijo {@code pedidos360.kafka-admin}. Los nombres de topicos y
 * grupos no viven aqui: esos estan en {@code pedidos360.streaming}, que es la unica fuente de
 * verdad de la topologia.
 */
@ConfigurationProperties(prefix = "pedidos360.kafka-admin")
public class AdminStreamingProperties {

  /** Segundos de espera en cada operacion contra el cluster. */
  private int timeoutSegundos = 10;

  /**
   * Si es true, los topicos y grupos que usa la aplicacion no se pueden eliminar desde el API.
   *
   * <p>Un topico borrado se lleva la historia con el: a diferencia de una cola vacia, no hay
   * manera de reconstruirlo.
   */
  private boolean protegerTopologiaDelSistema = true;

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
