package cl.duoc.pedidos360.mensajeria.consumo;

import cl.duoc.pedidos360.mensajeria.config.MensajeriaProperties;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Centraliza la confirmacion de mensajes y la politica de errores de todos los consumidores.
 *
 * <p>Tres caminos, siempre explicitos:
 *
 * <ul>
 *   <li><b>Exito</b>: {@code basicAck}, el mensaje sale de la cola.
 *   <li><b>Error recuperable</b> (el broker, la base o un servicio fallaron): el mensaje se
 *       reenvia a la cola de espera, que lo devuelve a la cola principal pasado el TTL. Al
 *       agotar los intentos configurados, se descarta a la DLQ.
 *   <li><b>Error no recuperable</b> ({@link EventoNoProcesableException}): {@code basicNack}
 *       sin reencolar, es decir, derecho a la DLQ y queda registrado en el log.
 * </ul>
 */
public class GestorConsumo {

  private static final Logger log = LoggerFactory.getLogger(GestorConsumo.class);

  /** Cabecera con la cantidad de intentos ya realizados sobre el mensaje. */
  public static final String CABECERA_INTENTOS = "x-pedidos360-intentos";

  /** Cabecera con el motivo por el que el mensaje termino descartado. */
  public static final String CABECERA_MOTIVO = "x-pedidos360-motivo";

  private final RabbitTemplate rabbitTemplate;
  private final MensajeriaProperties propiedades;

  public GestorConsumo(RabbitTemplate rabbitTemplate, MensajeriaProperties propiedades) {
    this.rabbitTemplate = rabbitTemplate;
    this.propiedades = propiedades;
  }

  /**
   * Ejecuta el procesamiento de un evento y resuelve la confirmacion del mensaje.
   *
   * @param <T> tipo del evento
   * @param evento evento ya deserializado
   * @param mensaje mensaje original, con sus cabeceras
   * @param canal canal AMQP sobre el que se confirma
   * @param referencia texto corto para los logs, por ejemplo el numero de pedido
   * @param accion trabajo de negocio a ejecutar
   * @throws IOException si el canal AMQP falla al confirmar
   */
  public <T> void procesar(
      T evento, Message mensaje, Channel canal, String referencia, Consumer<T> accion)
      throws IOException {
    long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
    String cola = colaOrigen(mensaje);
    try {
      accion.accept(evento);
      canal.basicAck(etiqueta, false);
      log.debug("Mensaje {} confirmado en {} ({})", etiqueta, cola, referencia);
    } catch (EventoNoProcesableException e) {
      log.error(
          "Evento no procesable en {} ({}): {}. Se envia a {}",
          cola,
          referencia,
          e.getMessage(),
          propiedades.dlqDe(cola));
      canal.basicNack(etiqueta, false, false);
    } catch (RuntimeException e) {
      manejarErrorRecuperable(mensaje, canal, cola, referencia, e);
    }
  }

  /**
   * Decide entre reintentar o descartar cuando el error podria resolverse solo.
   *
   * @param mensaje mensaje original
   * @param canal canal AMQP
   * @param cola cola de origen
   * @param referencia texto corto para los logs
   * @param error causa del fallo
   * @throws IOException si el canal AMQP falla al confirmar
   */
  private void manejarErrorRecuperable(
      Message mensaje, Channel canal, String cola, String referencia, RuntimeException error)
      throws IOException {
    long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
    int intentos = intentosPrevios(mensaje) + 1;

    if (intentos >= propiedades.getMaxIntentos()) {
      log.error(
          "Intento {}/{} fallido en {} ({}): {}. Se descarta a {}",
          intentos,
          propiedades.getMaxIntentos(),
          cola,
          referencia,
          error.getMessage(),
          propiedades.dlqDe(cola));
      mensaje.getMessageProperties().setHeader(CABECERA_MOTIVO, resumen(error));
      canal.basicNack(etiqueta, false, false);
      return;
    }

    try {
      mensaje.getMessageProperties().setHeader(CABECERA_INTENTOS, intentos);
      mensaje.getMessageProperties().setHeader(CABECERA_MOTIVO, resumen(error));
      rabbitTemplate.send("", propiedades.reintentoDe(cola), mensaje);
      canal.basicAck(etiqueta, false);
      log.warn(
          "Intento {}/{} fallido en {} ({}): {}. Reintento en {} ms",
          intentos,
          propiedades.getMaxIntentos(),
          cola,
          referencia,
          error.getMessage(),
          propiedades.getEsperaReintentoMs());
    } catch (RuntimeException fallaAlReintentar) {
      log.error(
          "No se pudo programar el reintento en {} ({}): {}. Se descarta a {}",
          cola,
          referencia,
          fallaAlReintentar.getMessage(),
          propiedades.dlqDe(cola));
      canal.basicNack(etiqueta, false, false);
    }
  }

  /**
   * Cola por la que llego el mensaje.
   *
   * @param mensaje mensaje recibido
   * @return nombre de la cola de origen
   */
  private String colaOrigen(Message mensaje) {
    String cola = mensaje.getMessageProperties().getConsumerQueue();
    return cola != null ? cola : "desconocida";
  }

  /**
   * Intentos ya realizados sobre el mensaje, leidos de su cabecera.
   *
   * @param mensaje mensaje recibido
   * @return cantidad de intentos previos
   */
  private int intentosPrevios(Message mensaje) {
    MessageProperties propiedadesMensaje = mensaje.getMessageProperties();
    Object valor = propiedadesMensaje.getHeader(CABECERA_INTENTOS);
    if (valor instanceof Number numero) {
      return numero.intValue();
    }
    return 0;
  }

  /**
   * Resumen corto del error para dejarlo en la cabecera del mensaje descartado.
   *
   * @param error excepcion capturada
   * @return tipo y mensaje del error
   */
  private String resumen(Throwable error) {
    String detalle = error.getMessage() == null ? "sin detalle" : error.getMessage();
    return error.getClass().getSimpleName() + ": " + detalle;
  }
}
