package cl.duoc.pedidos360.analitica.streaming;

import cl.duoc.pedidos360.analitica.service.AnaliticaVentasService;
import cl.duoc.pedidos360.analitica.service.OrigenRegistro;
import cl.duoc.pedidos360.streaming.consumo.ValidadorEventos;
import cl.duoc.pedidos360.streaming.evento.PedidoStreamEvento;
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
 * Consume los hechos de pedidos y los entrega al servicio de metricas.
 *
 * <p>Esta clase es la frontera con Kafka y hace solo cuatro cosas: recibir, validar, traducir
 * la metadata del registro y delegar. No suma montos ni decide nada de negocio; si manana el
 * flujo llegara por otro transporte, el servicio de metricas no cambiaria una linea.
 *
 * <p>Pertenece al grupo <b>analitica-ventas</b>. Con tres consumidores en el grupo y tres
 * particiones en el topico, Kafka asigna una particion a cada consumidor: los tres trabajan en
 * paralelo y, si uno cae, sus particiones se reasignan a los otros dos sin perder registros.
 */
@Component
public class VentasListener {

  private static final Logger log = LoggerFactory.getLogger(VentasListener.class);

  private final AnaliticaVentasService ventas;
  private final ValidadorEventos validador;

  public VentasListener(AnaliticaVentasService ventas, ValidadorEventos validador) {
    this.ventas = ventas;
    this.validador = validador;
  }

  /**
   * Procesa un hecho de pedido.
   *
   * <p>Lo que ocurre si esto lanza una excepcion lo decide el manejador de errores comun: un
   * fallo transitorio se reintenta con espera creciente y un registro invalido va directo al
   * topico de descarte.
   *
   * @param evento hecho de pedido deserializado
   * @param cabeceras cabeceras del registro
   * @param topico topico de origen
   * @param particion particion de origen
   * @param offset posicion del registro en la particion
   * @param clave clave de particionado
   * @param intento numero de entrega, util para distinguir un reintento de un registro nuevo
   */
  @KafkaListener(
      id = "analitica-ventas",
      topics = "${pedidos360.streaming.topicos.pedidos:pedidos360.pedidos.eventos}",
      groupId = "${pedidos360.streaming.grupos.analitica-ventas:pedidos360-analitica-ventas}",
      containerFactory = "streamingFabricaPedidos")
  public void recibir(
      @Payload PedidoStreamEvento evento,
      @Headers MessageHeaders cabeceras,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topico,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
      @Header(KafkaHeaders.OFFSET) long offset,
      @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String clave,
      @Header(name = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer intento) {
    validador.validar(evento, cabeceras);
    if (intento != null && intento > 1) {
      log.info(
          "Reintento {} del registro {}-{} offset {}", intento, topico, particion, offset);
    }
    boolean aplicado =
        ventas.procesar(evento, new OrigenRegistro(topico, particion, offset, clave));
    if (!aplicado) {
      log.debug("Registro {}-{} offset {} ya estaba aplicado", topico, particion, offset);
    }
  }
}
