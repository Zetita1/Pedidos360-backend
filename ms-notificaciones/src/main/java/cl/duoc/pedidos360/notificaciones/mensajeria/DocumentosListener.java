package cl.duoc.pedidos360.notificaciones.mensajeria;

import cl.duoc.pedidos360.mensajeria.consumo.GestorConsumo;
import cl.duoc.pedidos360.mensajeria.evento.PedidoConfirmadoEvento;
import cl.duoc.pedidos360.notificaciones.service.DocumentoService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor del dominio <b>documentos</b>: emite el ticket de cada pedido confirmado.
 *
 * <p>Esta separado de las notificaciones a proposito: son dos responsabilidades distintas,
 * con su propia cola y su propia DLQ, de modo que un problema generando tickets no frena los
 * avisos al cliente.
 */
@Component
public class DocumentosListener {

  private final DocumentoService servicio;
  private final GestorConsumo gestor;

  public DocumentosListener(DocumentoService servicio, GestorConsumo gestor) {
    this.servicio = servicio;
    this.gestor = gestor;
  }

  /**
   * Pedido confirmado: se emite su ticket.
   *
   * @param evento evento recibido
   * @param mensaje mensaje original con sus cabeceras
   * @param canal canal AMQP para confirmar o rechazar
   * @throws IOException si falla la confirmacion
   */
  @RabbitListener(
      queues = "${pedidos360.mensajeria.colas.documentos}",
      containerFactory = "mensajeriaListenerFactory")
  public void alConfirmarsePedido(
      PedidoConfirmadoEvento evento, Message mensaje, Channel canal) throws IOException {
    gestor.procesar(evento, mensaje, canal, evento.pedidoNumero(), servicio::emitirTicket);
  }
}
