package cl.duoc.pedidos360.analitica.streaming;

import cl.duoc.pedidos360.analitica.service.OrigenRegistro;
import cl.duoc.pedidos360.analitica.service.ProyeccionStockService;
import cl.duoc.pedidos360.streaming.consumo.ValidadorEventos;
import cl.duoc.pedidos360.streaming.evento.StockStreamEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consume los movimientos de stock y mantiene la proyeccion al dia.
 *
 * <p>Pertenece al grupo <b>proyeccion-stock</b>, distinto del de ventas aunque viva en el
 * mismo microservicio. Separarlos tiene un efecto concreto: cada grupo lleva su propio
 * avance, asi que un atasco procesando pedidos no detiene la actualizacion del stock, y se
 * puede reprocesar uno de los dos flujos desde el principio sin tocar el otro.
 *
 * <p>El topico esta compactado y el grupo lee desde el comienzo la primera vez, de modo que al
 * arrancar sin datos la proyeccion se reconstruye sola con el ultimo registro de cada
 * producto.
 */
@Component
public class StockListener {

  private static final Logger log = LoggerFactory.getLogger(StockListener.class);

  private final ProyeccionStockService stock;
  private final ValidadorEventos validador;

  public StockListener(ProyeccionStockService stock, ValidadorEventos validador) {
    this.stock = stock;
    this.validador = validador;
  }

  /**
   * Procesa un movimiento de stock.
   *
   * @param evento movimiento deserializado
   * @param cabeceras cabeceras del registro
   * @param topico topico de origen
   * @param particion particion de origen
   * @param offset posicion del registro en la particion
   * @param clave clave de particionado
   */
  @KafkaListener(
      id = "proyeccion-stock",
      topics = "${pedidos360.streaming.topicos.stock:pedidos360.inventario.stock}",
      groupId = "${pedidos360.streaming.grupos.proyeccion-stock:pedidos360-proyeccion-stock}",
      containerFactory = "streamingFabricaStock")
  public void recibir(
      @Payload StockStreamEvento evento,
      @Headers MessageHeaders cabeceras,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topico,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
      @Header(KafkaHeaders.OFFSET) long offset,
      @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String clave) {
    validador.validar(evento, cabeceras);
    boolean aplicado =
        stock.procesar(evento, new OrigenRegistro(topico, particion, offset, clave));
    if (!aplicado) {
      log.debug("Movimiento {}-{} offset {} ya estaba aplicado", topico, particion, offset);
    }
  }
}
