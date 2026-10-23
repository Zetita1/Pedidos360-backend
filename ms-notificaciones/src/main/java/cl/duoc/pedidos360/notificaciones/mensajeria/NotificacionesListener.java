package cl.duoc.pedidos360.notificaciones.mensajeria;

import cl.duoc.pedidos360.mensajeria.consumo.GestorConsumo;
import cl.duoc.pedidos360.mensajeria.evento.PedidoCreadoEvento;
import cl.duoc.pedidos360.mensajeria.evento.PedidoEstadoCambiadoEvento;
import cl.duoc.pedidos360.notificaciones.service.NotificacionService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidores del dominio <b>notificaciones</b>: todo lo que implica avisarle algo al
 * cliente.
 *
 * <p>La cola y la fabrica de listeners llegan por configuracion; aqui no hay nombres
 * escritos a mano. Cada metodo delega el trabajo en el servicio de negocio y deja la
 * confirmacion del mensaje en manos de {@link GestorConsumo}.
 */
@Component
@RabbitListener(
    queues = "${pedidos360.mensajeria.colas.notificaciones}",
    containerFactory = "mensajeriaListenerFactory")
public class NotificacionesListener {

  private static final Logger log = LoggerFactory.getLogger(NotificacionesListener.class);

  private final NotificacionService servicio;
  private final GestorConsumo gestor;

  public NotificacionesListener(NotificacionService servicio, GestorConsumo gestor) {
    this.servicio = servicio;
    this.gestor = gestor;
  }

  /**
   * Pedido recien creado: se avisa al cliente que lo recibimos.
   *
   * @param evento evento recibido
   * @param mensaje mensaje original con sus cabeceras
   * @param canal canal AMQP para confirmar o rechazar
   * @throws IOException si falla la confirmacion
   */
  @RabbitHandler
  public void alCrearsePedido(PedidoCreadoEvento evento, Message mensaje, Channel canal)
      throws IOException {
    gestor.procesar(evento, mensaje, canal, evento.pedidoNumero(), servicio::registrarRecepcion);
  }

  /**
   * Pedido que cambia de estado: se avisa el seguimiento.
   *
   * @param evento evento recibido
   * @param mensaje mensaje original con sus cabeceras
   * @param canal canal AMQP para confirmar o rechazar
   * @throws IOException si falla la confirmacion
   */
  @RabbitHandler
  public void alCambiarEstado(PedidoEstadoCambiadoEvento evento, Message mensaje, Channel canal)
      throws IOException {
    gestor.procesar(evento, mensaje, canal, evento.pedidoNumero(), servicio::registrarSeguimiento);
  }

  /**
   * Mensaje con un tipo que esta cola no sabe tratar: se descarta a la DLQ sin reintentos,
   * porque reintentarlo daria siempre el mismo resultado.
   *
   * @param contenido cuerpo del mensaje
   * @param mensaje mensaje original
   * @param canal canal AMQP
   * @throws IOException si falla el rechazo
   */
  @RabbitHandler(isDefault = true)
  public void mensajeDesconocido(Object contenido, Message mensaje, Channel canal)
      throws IOException {
    log.error(
        "Mensaje de tipo inesperado en la cola de notificaciones: {}. Se envia a la DLQ",
        contenido == null ? "null" : contenido.getClass().getName());
    canal.basicNack(mensaje.getMessageProperties().getDeliveryTag(), false, false);
  }
}
