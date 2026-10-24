package cl.duoc.pedidos360.notificaciones.mensajeria;

import cl.duoc.pedidos360.mensajeria.consumo.GestorConsumo;
import cl.duoc.pedidos360.mensajeria.evento.PedidoAnuladoEvento;
import cl.duoc.pedidos360.notificaciones.service.AuditoriaService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor del dominio <b>auditoria</b>: registra las operaciones sensibles.
 *
 * <p>Su cola se alimenta del exchange <b>direct</b>, con coincidencia exacta de la routing
 * key: el comando tiene un unico destinatario y no debe difundirse a otros consumidores.
 */
@Component
public class AuditoriaListener {

  private final AuditoriaService servicio;
  private final GestorConsumo gestor;

  public AuditoriaListener(AuditoriaService servicio, GestorConsumo gestor) {
    this.servicio = servicio;
    this.gestor = gestor;
  }

  /**
   * Pedido anulado: se deja constancia de quien lo anulo y por que.
   *
   * @param evento comando recibido
   * @param mensaje mensaje original con sus cabeceras
   * @param canal canal AMQP para confirmar o rechazar
   * @throws IOException si falla la confirmacion
   */
  @RabbitListener(
      queues = "${pedidos360.mensajeria.colas.auditoria}",
      containerFactory = "mensajeriaListenerFactory")
  public void alAnularsePedido(PedidoAnuladoEvento evento, Message mensaje, Channel canal)
      throws IOException {
    gestor.procesar(evento, mensaje, canal, evento.pedidoNumero(), servicio::registrarAnulacion);
  }
}
