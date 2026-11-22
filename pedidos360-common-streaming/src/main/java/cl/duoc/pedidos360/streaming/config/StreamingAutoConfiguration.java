package cl.duoc.pedidos360.streaming.config;

import cl.duoc.pedidos360.streaming.consumo.EventoStreamInvalidoException;
import cl.duoc.pedidos360.streaming.consumo.ValidadorEventos;
import cl.duoc.pedidos360.streaming.evento.PedidoStreamEvento;
import cl.duoc.pedidos360.streaming.evento.StockStreamEvento;
import cl.duoc.pedidos360.streaming.publicacion.KafkaPublicadorStream;
import cl.duoc.pedidos360.streaming.publicacion.PublicadorStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Infraestructura de streaming compartida por todos los microservicios: productor, fabricas
 * de listeners tipadas y la politica de errores con reintentos y descarte.
 *
 * <p>Se activa sola al tener el modulo en el classpath. La conexion al cluster se lee de
 * {@code spring.kafka} —bootstrap servers, serializers, acks, grupos, tamanos de lote— y lo
 * que decide el proyecto vive en {@code pedidos360.streaming}.
 *
 * <p>Hay dos fabricas de listeners, una por topico, cada una con el deserializador apuntando
 * al DTO exacto de ese flujo. Es deliberado: el tipo no se infiere de una cabecera que viaja
 * en el mensaje, sino que esta fijado en la configuracion del consumidor. Un productor no
 * puede inducir a deserializar una clase distinta de la que el topico declara.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(StreamingProperties.class)
@EnableKafka
@Import(TopicosStreamingConfig.class)
public class StreamingAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(StreamingAutoConfiguration.class);

  /**
   * Productor de los eventos de negocio.
   *
   * <p>Las garantias de escritura se configuran en {@code spring.kafka.producer}: {@code
   * acks=all} junto a {@code min.insync.replicas=2} en el topico significa que un registro se
   * da por escrito cuando dos de las tres replicas lo tienen, e {@code enable.idempotence}
   * evita que un reintento del propio productor lo duplique.
   *
   * @param propiedadesKafka configuracion del cliente leida de spring.kafka
   * @param objectMapper mapper de la aplicacion, para respetar el formato de fechas y montos
   * @return fabrica de productores con clave de texto y valor JSON
   */
  @Bean
  @Primary
  public ProducerFactory<String, Object> streamingProducerFactory(
      KafkaProperties propiedadesKafka, ObjectMapper objectMapper) {
    JsonSerializer<Object> serializador = new JsonSerializer<>(objectMapper);
    // El tipo Java no viaja en las cabeceras: cada consumidor ya sabe que DTO espera y asi el
    // contrato del topico no depende de los nombres de clase del productor.
    serializador.setAddTypeInfo(false);
    return new DefaultKafkaProducerFactory<>(
        propiedadesKafka.buildProducerProperties(null), new StringSerializer(), serializador);
  }

  /**
   * Template usado por los servicios para publicar.
   *
   * @param fabrica fabrica de productores
   * @return template con clave de texto y valor JSON
   */
  @Bean
  @Primary
  public KafkaTemplate<String, Object> streamingKafkaTemplate(
      ProducerFactory<String, Object> fabrica) {
    return new KafkaTemplate<>(fabrica);
  }

  /**
   * Publicador de eventos de negocio hacia los topicos.
   *
   * @param template template de publicacion
   * @param propiedades nombres de topicos del proyecto
   * @return publicador que resuelve topico y clave de cada evento
   */
  @Bean
  @ConditionalOnMissingBean(PublicadorStream.class)
  public PublicadorStream publicadorStream(
      KafkaTemplate<String, Object> template, StreamingProperties propiedades) {
    return new KafkaPublicadorStream(template, propiedades);
  }

  /**
   * Validador de los registros entrantes.
   *
   * @param validator validador de Bean Validation
   * @return validador compartido por los consumidores
   */
  @Bean
  @ConditionalOnMissingBean(ValidadorEventos.class)
  public ValidadorEventos validadorEventos(Validator validator) {
    return new ValidadorEventos(validator);
  }

  /**
   * Manejador de errores de consumo: reintenta lo transitorio y descarta lo que no tiene
   * arreglo.
   *
   * <p>Tres caminos:
   *
   * <ul>
   *   <li>Error transitorio (la base no responde, un servicio vecino esta caido): reintenta
   *       con espera creciente, hasta {@code maxIntentos}. La particion se detiene mientras
   *       tanto, que es lo correcto: mantiene el orden y no adelanta registros posteriores.
   *   <li>Registro invalido o imposible de deserializar: va al topico de descarte en el primer
   *       intento. Reintentar no cambiaria el contenido.
   *   <li>Agotados los reintentos: va al topico de descarte, se confirma el offset y la
   *       particion sigue avanzando. Un solo registro defectuoso no bloquea el flujo.
   * </ul>
   *
   * @param recuperador publicador hacia los topicos de descarte
   * @param propiedades politica de reintentos del proyecto
   * @return manejador usado por las dos fabricas de listeners
   */
  @Bean
  @ConditionalOnMissingBean(DefaultErrorHandler.class)
  public DefaultErrorHandler streamingErrorHandler(
      DeadLetterPublishingRecoverer recuperador, StreamingProperties propiedades) {
    ExponentialBackOff espera = new ExponentialBackOff();
    espera.setInitialInterval(propiedades.getEsperaReintentoMs());
    espera.setMultiplier(2.0);
    espera.setMaxInterval(propiedades.getEsperaReintentoMs() * 8);
    // maxIntentos cuenta la entrega original, de modo que los reintentos son uno menos.
    espera.setMaxAttempts(Math.max(propiedades.getMaxIntentos() - 1, 0));

    DefaultErrorHandler manejador = new DefaultErrorHandler(recuperador, espera);
    manejador.addNotRetryableExceptions(EventoStreamInvalidoException.class);
    manejador.setCommitRecovered(true);
    manejador.setLogLevel(KafkaException.Level.WARN);
    manejador.setRetryListeners(new RegistroDeIntentos());
    return manejador;
  }

  /**
   * Publica en el topico de descarte el registro que no se pudo procesar.
   *
   * <p>Conserva el numero de particion de origen: los descartes de una misma clave quedan
   * juntos y pueden reprocesarse en el mismo orden en que fallaron. Las cabeceras que agrega
   * Spring Kafka dejan en el propio registro el topico original, el offset y la excepcion.
   *
   * <p>Se registran dos templates: el de JSON para los registros que si se deserializaron, y
   * uno de bytes para los que no. Un registro ilegible viaja al descarte tal como llego, sin
   * intentar interpretarlo, que es la unica forma de poder analizarlo despues.
   *
   * @param templateJson template de valores JSON
   * @param propiedadesKafka configuracion del cliente
   * @param propiedades nombres de topicos del proyecto
   * @return recuperador que deriva al DLT
   */
  @Bean
  @ConditionalOnMissingBean(DeadLetterPublishingRecoverer.class)
  public DeadLetterPublishingRecoverer streamingDlt(
      KafkaTemplate<String, Object> templateJson,
      KafkaProperties propiedadesKafka,
      StreamingProperties propiedades) {
    KafkaTemplate<String, byte[]> templateBytes =
        new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(
                propiedadesKafka.buildProducerProperties(null),
                new StringSerializer(),
                new ByteArraySerializer()));

    Map<Class<?>, KafkaOperations<?, ?>> plantillas = new LinkedHashMap<>();
    plantillas.put(byte[].class, templateBytes);
    plantillas.put(Object.class, templateJson);

    return new DeadLetterPublishingRecoverer(
        plantillas,
        (registro, excepcion) -> {
          String destino = propiedades.dltDe(registro.topic());
          log.error(
              "Registro derivado a {} desde {}-{} offset {} (clave {}): {}",
              destino,
              registro.topic(),
              registro.partition(),
              registro.offset(),
              registro.key(),
              excepcion.getMessage());
          return new TopicPartition(destino, registro.partition());
        });
  }

  /**
   * Fabrica de listeners del flujo de pedidos.
   *
   * <p>La concurrencia se iguala al numero de particiones: con tres consumidores en el mismo
   * grupo, Kafka asigna una particion a cada uno y los tres procesan en paralelo. Poner mas
   * no sirve —quedarian inactivos— y poner menos deja a un consumidor con dos particiones.
   *
   * @param propiedadesKafka configuracion del cliente
   * @param objectMapper mapper de la aplicacion
   * @param manejador politica de errores
   * @param propiedades parametros del proyecto
   * @return fabrica para los listener del topico de pedidos
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, PedidoStreamEvento>
      streamingFabricaPedidos(
          KafkaProperties propiedadesKafka,
          ObjectMapper objectMapper,
          DefaultErrorHandler manejador,
          StreamingProperties propiedades) {
    return fabrica(
        consumidorDe(PedidoStreamEvento.class, propiedadesKafka, objectMapper),
        manejador,
        propiedades);
  }

  /**
   * Fabrica de listeners del flujo de stock.
   *
   * @param propiedadesKafka configuracion del cliente
   * @param objectMapper mapper de la aplicacion
   * @param manejador politica de errores
   * @param propiedades parametros del proyecto
   * @return fabrica para los listener del topico de stock
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, StockStreamEvento> streamingFabricaStock(
      KafkaProperties propiedadesKafka,
      ObjectMapper objectMapper,
      DefaultErrorHandler manejador,
      StreamingProperties propiedades) {
    return fabrica(
        consumidorDe(StockStreamEvento.class, propiedadesKafka, objectMapper),
        manejador,
        propiedades);
  }

  /**
   * Arma una fabrica de listeners sobre una fabrica de consumidores ya tipada.
   *
   * @param <T> tipo del DTO que consume
   * @param consumidores fabrica de consumidores
   * @param manejador politica de errores
   * @param propiedades parametros del proyecto
   * @return fabrica de listeners configurada
   */
  private <T> ConcurrentKafkaListenerContainerFactory<String, T> fabrica(
      ConsumerFactory<String, T> consumidores,
      DefaultErrorHandler manejador,
      StreamingProperties propiedades) {
    ConcurrentKafkaListenerContainerFactory<String, T> fabrica =
        new ConcurrentKafkaListenerContainerFactory<>();
    fabrica.setConsumerFactory(consumidores);
    fabrica.setConcurrency(propiedades.getConsumidores());
    fabrica.setCommonErrorHandler(manejador);
    // Deja disponible el numero de intento en el contexto del listener, para poder registrarlo.
    fabrica.getContainerProperties().setDeliveryAttemptHeader(true);
    return fabrica;
  }

  /**
   * Fabrica de consumidores con el deserializador apuntando a un DTO concreto.
   *
   * <p>El deserializador JSON va envuelto en {@code ErrorHandlingDeserializer}: si el cuerpo
   * no es el JSON esperado, el fallo se entrega al manejador de errores como un registro mas
   * en vez de reventar el hilo del contenedor. Sin esa envoltura, un unico mensaje mal
   * formado dejaria la particion detenida reintentando la deserializacion para siempre.
   *
   * @param <T> tipo del DTO
   * @param tipo clase del DTO esperado en el topico
   * @param propiedadesKafka configuracion del cliente
   * @param objectMapper mapper de la aplicacion
   * @return fabrica de consumidores tipada
   */
  private <T> ConsumerFactory<String, T> consumidorDe(
      Class<T> tipo, KafkaProperties propiedadesKafka, ObjectMapper objectMapper) {
    JsonDeserializer<T> deserializador = new JsonDeserializer<>(tipo, objectMapper, false);
    deserializador.ignoreTypeHeaders();
    return new DefaultKafkaConsumerFactory<>(
        propiedadesKafka.buildConsumerProperties(null),
        new StringDeserializer(),
        new ErrorHandlingDeserializer<>(deserializador));
  }

  /**
   * Deja en el log cada reintento y cada descarte.
   *
   * <p>Es la fuente con la que se explica un lag que sube: el numero de intento dice si el
   * consumo esta reintentando o avanzando, y la ubicacion exacta permite ir a buscar el
   * registro en Kafka UI.
   */
  private static final class RegistroDeIntentos
      implements org.springframework.kafka.listener.RetryListener {

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> registro, Exception excepcion, int intento) {
      log.warn(
          "Intento {} fallido en {}-{} offset {} (clave {}): {}",
          intento,
          registro.topic(),
          registro.partition(),
          registro.offset(),
          registro.key(),
          excepcion.getMessage());
    }

    @Override
    public void recovered(ConsumerRecord<?, ?> registro, Exception excepcion) {
      log.info(
          "Registro de {}-{} offset {} resuelto tras los reintentos",
          registro.topic(),
          registro.partition(),
          registro.offset());
    }

    @Override
    public void recoveryFailed(
        ConsumerRecord<?, ?> registro, Exception original, Exception alDescartar) {
      log.error(
          "No fue posible derivar al descarte el registro de {}-{} offset {}: {}",
          registro.topic(),
          registro.partition(),
          registro.offset(),
          alDescartar.getMessage());
    }
  }
}
