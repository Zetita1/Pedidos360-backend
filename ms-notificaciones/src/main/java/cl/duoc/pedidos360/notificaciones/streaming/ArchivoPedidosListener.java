package cl.duoc.pedidos360.notificaciones.streaming;

import cl.duoc.pedidos360.notificaciones.service.ArchivoPedidosService;
import cl.duoc.pedidos360.streaming.consumo.ValidadorEventos;
import cl.duoc.pedidos360.streaming.evento.PedidoStreamEvento;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Archiva los hechos de pedidos leyendo el mismo topico que la analitica.
 *
 * <p>Vive en otro microservicio y pertenece al grupo <b>archivo-pedidos</b>. Eso es lo que
 * hace que ambos reciban todos los registros: Kafka reparte las particiones <i>dentro</i> de
 * un grupo, pero entrega el flujo completo a <i>cada</i> grupo. Un consumidor nuevo se suma
 * sin pedirle permiso a los demas y sin quitarles mensajes.
 *
 * <p>Cada grupo lleva su propio avance, de modo que este archivo puede ir atrasado, caerse o
 * reprocesarse desde cero sin afectar en nada a las metricas. Es la diferencia de fondo con
 * una cola: en RabbitMQ el mensaje consumido deja de estar disponible, aqui el registro sigue
 * en el topico y cada interesado decide hasta donde lo leyo.
 */
@Component
public class ArchivoPedidosListener {

  private final ArchivoPedidosService archivo;
  private final ValidadorEventos validador;

  public ArchivoPedidosListener(ArchivoPedidosService archivo, ValidadorEventos validador) {
    this.archivo = archivo;
    this.validador = validador;
  }

  /**
   * Archiva un hecho de pedido.
   *
   * @param evento hecho deserializado
   * @param cabeceras cabeceras del registro
   * @param topico topico de origen
   * @param particion particion de origen
   * @param offset posicion en la particion
   */
  @KafkaListener(
      id = "archivo-pedidos",
      topics = "${pedidos360.streaming.topicos.pedidos:pedidos360.pedidos.eventos}",
      groupId = "${pedidos360.streaming.grupos.archivo-pedidos:pedidos360-archivo-pedidos}",
      containerFactory = "streamingFabricaPedidos")
  public void recibir(
      @Payload PedidoStreamEvento evento,
      @Headers MessageHeaders cabeceras,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topico,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
      @Header(KafkaHeaders.OFFSET) long offset) {
    validador.validar(evento, cabeceras);
    archivo.archivar(evento, topico, particion, offset);
  }
}
