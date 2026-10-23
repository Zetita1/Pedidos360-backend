package cl.duoc.pedidos360.mensajeria.publicacion;

import cl.duoc.pedidos360.mensajeria.config.MensajeriaProperties;
import cl.duoc.pedidos360.mensajeria.evento.EventoPedidos360;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Implementacion sobre RabbitMQ: resuelve la routing key del evento desde la configuracion
 * centralizada y lo publica como mensaje persistente.
 *
 * <p>Si el broker no esta disponible, el error se registra y no se propaga: la mensajeria es
 * asincrona y no debe tumbar la operacion de negocio que ya quedo guardada.
 */
public class RabbitPublicadorEventos implements PublicadorEventos {

  private static final Logger log = LoggerFactory.getLogger(RabbitPublicadorEventos.class);

  private final RabbitTemplate rabbitTemplate;
  private final MensajeriaProperties propiedades;

  public RabbitPublicadorEventos(
      RabbitTemplate rabbitTemplate, MensajeriaProperties propiedades) {
    this.rabbitTemplate = rabbitTemplate;
    this.propiedades = propiedades;
  }

  @Override
  public void publicar(EventoPedidos360 evento) {
    String rutaEvento = propiedades.ruta(evento.nombreLogico());
    try {
      rabbitTemplate.convertAndSend(
          propiedades.getExchange(),
          rutaEvento,
          evento,
          mensaje -> {
            mensaje.getMessageProperties().setMessageId(evento.eventoId());
            mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            mensaje.getMessageProperties().setContentEncoding("UTF-8");
            return mensaje;
          });
      log.info(
          "Evento {} publicado en {} con routing key {} (pedido {})",
          evento.nombreLogico(),
          propiedades.getExchange(),
          rutaEvento,
          evento.pedidoNumero());
    } catch (AmqpException e) {
      log.error(
          "No fue posible publicar el evento {} del pedido {}: {}",
          evento.nombreLogico(),
          evento.pedidoNumero(),
          e.getMessage());
    }
  }
}
