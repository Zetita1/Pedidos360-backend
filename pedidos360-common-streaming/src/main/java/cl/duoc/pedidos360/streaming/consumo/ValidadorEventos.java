package cl.duoc.pedidos360.streaming.consumo;

import cl.duoc.pedidos360.streaming.evento.EventoStream;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageHeaders;

/**
 * Revisa que el registro traiga lo que el consumidor necesita antes de dejarlo entrar a la
 * logica de negocio.
 *
 * <p>Un topico es una frontera: cualquiera que tenga permiso de escritura puede publicar en
 * el, y el productor puede cambiar de version antes que el consumidor. Validar en la entrada
 * evita que un registro incompleto se descubra a mitad del procesamiento, cuando ya se
 * escribio parte del resultado.
 *
 * <p>Las reglas son las anotaciones de los propios DTO, asi que el contrato se lee en el
 * evento y no queda repartido entre los consumidores.
 */
public class ValidadorEventos {

  private static final Logger log = LoggerFactory.getLogger(ValidadorEventos.class);

  private final Validator validator;

  public ValidadorEventos(Validator validator) {
    this.validator = validator;
  }

  /**
   * Valida el registro y deja constancia de donde venia.
   *
   * @param evento registro recibido
   * @param cabeceras cabeceras del mensaje, de donde salen topico, particion y offset
   * @throws EventoStreamInvalidoException si el registro incumple su contrato
   */
  public void validar(EventoStream evento, MessageHeaders cabeceras) {
    if (evento == null) {
      throw new EventoStreamInvalidoException(
          "El registro llego vacio en " + ubicacion(cabeceras));
    }
    Set<ConstraintViolation<EventoStream>> incumplimientos = validator.validate(evento);
    if (incumplimientos.isEmpty()) {
      return;
    }
    String detalle =
        incumplimientos.stream()
            .map(falla -> falla.getPropertyPath() + " " + falla.getMessage())
            .sorted()
            .collect(Collectors.joining("; "));
    // El log nombra el origen exacto para poder ir a buscar el registro en Kafka UI.
    log.warn(
        "Registro invalido descartado en {}: evento={} motivo={}",
        ubicacion(cabeceras),
        evento.eventoId(),
        detalle);
    throw new EventoStreamInvalidoException(detalle);
  }

  /**
   * Describe el origen del registro como {@code topico-particion@offset}.
   *
   * @param cabeceras cabeceras del mensaje
   * @return ubicacion legible del registro
   */
  public static String ubicacion(MessageHeaders cabeceras) {
    if (cabeceras == null) {
      return "origen desconocido";
    }
    return cabeceras.get(KafkaHeaders.RECEIVED_TOPIC)
        + "-"
        + cabeceras.get(KafkaHeaders.RECEIVED_PARTITION)
        + "@"
        + cabeceras.get(KafkaHeaders.OFFSET);
  }
}
