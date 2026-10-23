package cl.duoc.pedidos360.mensajeria.publicacion;

import cl.duoc.pedidos360.mensajeria.evento.EventoPedidos360;

/**
 * Puerta de salida de los eventos de dominio.
 *
 * <p>Los servicios de negocio dependen de esta interfaz y entregan el evento ya construido:
 * no conocen el exchange, la routing key ni el formato del mensaje.
 */
public interface PublicadorEventos {

  /**
   * Publica un evento en el exchange de eventos con la routing key que le corresponde.
   *
   * @param evento evento de dominio a publicar
   */
  void publicar(EventoPedidos360 evento);
}
