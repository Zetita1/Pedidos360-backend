package cl.duoc.pedidos360.streaming.publicacion;

import cl.duoc.pedidos360.streaming.config.StreamingProperties;
import cl.duoc.pedidos360.streaming.evento.EventoStream;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Productor de los topicos de Pedidos360.
 *
 * <p>Resuelve el topico desde la configuracion, publica con la clave de particionado que
 * declara el propio evento y registra en que particion y offset quedo cada registro, que es
 * lo que permite seguir el rastro de un evento desde el log hasta Kafka UI.
 *
 * <p>El envio es asincrono y su resultado se registra en el callback. Un fallo al publicar se
 * anota como error pero no se propaga: el hecho de negocio ya ocurrio y esta persistido, y
 * tumbar la operacion del usuario porque la analitica no esta disponible seria volver a
 * acoplar lo que el streaming vino a separar.
 */
public class KafkaPublicadorStream implements PublicadorStream {

  private static final Logger log = LoggerFactory.getLogger(KafkaPublicadorStream.class);

  /** Cabecera con el identificador del evento, para rastrearlo sin abrir el cuerpo. */
  private static final String CABECERA_EVENTO = "pedidos360-evento-id";

  /** Cabecera con el flujo de origen, util al revisar un topico de descarte. */
  private static final String CABECERA_FLUJO = "pedidos360-flujo";

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final StreamingProperties propiedades;

  public KafkaPublicadorStream(
      KafkaTemplate<String, Object> kafkaTemplate, StreamingProperties propiedades) {
    this.kafkaTemplate = kafkaTemplate;
    this.propiedades = propiedades;
  }

  @Override
  public void publicar(String flujo, EventoStream evento) {
    String topico = propiedades.topico(flujo);
    ProducerRecord<String, Object> registro =
        new ProducerRecord<>(topico, null, evento.clave(), evento);
    registro.headers().add(cabecera(CABECERA_EVENTO, evento.eventoId()));
    registro.headers().add(cabecera(CABECERA_FLUJO, flujo));

    try {
      kafkaTemplate
          .send(registro)
          .whenComplete(
              (resultado, error) -> {
                if (error != null) {
                  log.error(
                      "No fue posible publicar el evento {} en el topico {} (clave {}): {}",
                      evento.eventoId(),
                      topico,
                      evento.clave(),
                      error.getMessage());
                  return;
                }
                log.info(
                    "Evento {} publicado en {}-{} offset {} (clave {})",
                    evento.eventoId(),
                    resultado.getRecordMetadata().topic(),
                    resultado.getRecordMetadata().partition(),
                    resultado.getRecordMetadata().offset(),
                    evento.clave());
              });
    } catch (KafkaException | IllegalStateException e) {
      // El envio es asincrono, pero no todo falla despues: crear el productor o pedir el
      // metadato del topico ocurre en este mismo hilo y puede fallar aqui mismo. Sin este
      // catch, un cluster inalcanzable le devolveria un error 500 al usuario por un pedido
      // que ya quedo guardado.
      log.error(
          "No fue posible entregar el evento {} al productor de {} (clave {}): {}",
          evento.eventoId(),
          topico,
          evento.clave(),
          e.getMessage());
    }
  }

  private static RecordHeader cabecera(String nombre, String valor) {
    return new RecordHeader(
        nombre, valor == null ? null : valor.getBytes(StandardCharsets.UTF_8));
  }
}
