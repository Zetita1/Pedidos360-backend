package cl.duoc.pedidos360.mensajeria.evento;

import cl.duoc.pedidos360.mensajeria.config.TipoExchange;
import java.time.Instant;

/**
 * Contrato comun de los eventos de dominio que viajan por RabbitMQ.
 *
 * <p>Cada evento declara su propio nombre logico; la routing key con la que se publica se
 * resuelve desde {@code pedidos360.mensajeria.rutas}, nunca dentro del evento.
 */
public sealed interface EventoPedidos360
    permits
        PedidoCreadoEvento,
        PedidoConfirmadoEvento,
        PedidoEstadoCambiadoEvento,
        PedidoAnuladoEvento {

  /** Identificador unico del evento, util para rastrearlo en los logs y evitar duplicados. */
  String eventoId();

  /** Momento en que ocurrio el hecho de negocio. */
  Instant ocurridoEn();

  /** Pedido al que se refiere el evento. */
  String pedidoNumero();

  /**
   * Nombre logico del evento, la clave que lo une con su routing key configurada.
   *
   * @return nombre logico, por ejemplo {@code pedido-creado}
   */
  String nombreLogico();

  /**
   * Exchange por el que sale el evento. Por defecto el de hechos de dominio (topic); los
   * comandos con destinatario unico lo sobrescriben.
   *
   * @return tipo de exchange
   */
  default TipoExchange tipoExchange() {
    return TipoExchange.EVENTOS;
  }
}
