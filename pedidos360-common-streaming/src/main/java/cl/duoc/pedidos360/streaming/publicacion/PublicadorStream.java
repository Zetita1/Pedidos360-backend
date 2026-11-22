package cl.duoc.pedidos360.streaming.publicacion;

import cl.duoc.pedidos360.streaming.evento.EventoStream;

/**
 * Puerta de salida de los registros que alimentan los topicos de Kafka.
 *
 * <p>Los servicios de negocio dependen de esta interfaz y entregan el evento ya construido:
 * no conocen el nombre del topico, la clave de particionado ni el formato del mensaje.
 */
public interface PublicadorStream {

  /**
   * Publica un registro en el topico del flujo indicado.
   *
   * @param flujo nombre logico del flujo, por ejemplo {@code pedidos} o {@code stock}
   * @param evento registro a publicar
   */
  void publicar(String flujo, EventoStream evento);
}
