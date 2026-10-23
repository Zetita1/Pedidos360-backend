package cl.duoc.pedidos360.mensajeria.config;

import cl.duoc.pedidos360.mensajeria.consumo.GestorConsumo;
import cl.duoc.pedidos360.mensajeria.publicacion.PublicadorEventos;
import cl.duoc.pedidos360.mensajeria.publicacion.RabbitPublicadorEventos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Infraestructura de mensajeria compartida por todos los microservicios: conversion a JSON,
 * publicador de eventos y fabrica de listeners con confirmacion manual.
 *
 * <p>Se activa sola al tener el modulo en el classpath, de modo que ningun microservicio
 * repite esta configuracion ni la mezcla con su logica de negocio.
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@EnableConfigurationProperties(MensajeriaProperties.class)
@EnableRabbit
@Import(TopologiaMensajeriaConfig.class)
public class MensajeriaAutoConfiguration {

  /**
   * Paquetes cuyos tipos se aceptan al deserializar un mensaje entrante.
   *
   * <p>Se nombra el paquete exacto de los eventos: la lista no admite comodines parciales,
   * y abrirla con "*" permitiria deserializar cualquier clase que llegue en la cabecera
   * {@code __TypeId__}.
   */
  private static final String[] PAQUETES_CONFIABLES = {
    "cl.duoc.pedidos360.mensajeria.evento", "java.util", "java.lang"
  };

  /**
   * Convierte los eventos a JSON y de vuelta, usando el {@code ObjectMapper} de la aplicacion
   * para respetar el formato de fechas y de montos.
   *
   * @param objectMapper mapper configurado por Spring Boot
   * @return convertidor compartido por el template y por los listeners
   */
  @Bean
  @ConditionalOnMissingBean(MessageConverter.class)
  public MessageConverter mensajeriaMessageConverter(ObjectMapper objectMapper) {
    Jackson2JsonMessageConverter convertidor = new Jackson2JsonMessageConverter(objectMapper);
    DefaultJackson2JavaTypeMapper mapeador = new DefaultJackson2JavaTypeMapper();
    mapeador.setTrustedPackages(PAQUETES_CONFIABLES);
    convertidor.setJavaTypeMapper(mapeador);
    return convertidor;
  }

  /**
   * Fabrica de listeners con <b>ACK manual</b>: cada consumidor decide si confirma, reintenta
   * o descarta el mensaje. Un rechazo nunca se reencola de forma automatica, para que el
   * mensaje siga la ruta de reintento o la DLQ segun corresponda.
   *
   * @param connectionFactory conexion a RabbitMQ
   * @param convertidor convertidor de mensajes
   * @param propiedades parametros de mensajeria
   * @return fabrica usada por todos los {@code @RabbitListener} del sistema
   */
  @Bean
  public SimpleRabbitListenerContainerFactory mensajeriaListenerFactory(
      ConnectionFactory connectionFactory,
      MessageConverter convertidor,
      MensajeriaProperties propiedades) {
    SimpleRabbitListenerContainerFactory fabrica = new SimpleRabbitListenerContainerFactory();
    fabrica.setConnectionFactory(connectionFactory);
    fabrica.setMessageConverter(convertidor);
    fabrica.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    fabrica.setDefaultRequeueRejected(false);
    fabrica.setConcurrentConsumers(propiedades.getConsumidores());
    fabrica.setMaxConcurrentConsumers(Math.max(propiedades.getConsumidores(), 4));
    fabrica.setPrefetchCount(propiedades.getConsumidores());
    return fabrica;
  }

  /**
   * Publicador de eventos de dominio.
   *
   * @param rabbitTemplate template configurado por Spring Boot
   * @param propiedades parametros de mensajeria
   * @return publicador que resuelve la routing key de cada evento
   */
  @Bean
  @ConditionalOnMissingBean(PublicadorEventos.class)
  public PublicadorEventos publicadorEventos(
      RabbitTemplate rabbitTemplate, MensajeriaProperties propiedades) {
    return new RabbitPublicadorEventos(rabbitTemplate, propiedades);
  }

  /**
   * Gestor de confirmacion y errores que usan los consumidores.
   *
   * @param rabbitTemplate template para republicar en la cola de reintento
   * @param propiedades parametros de mensajeria
   * @return gestor compartido por los listeners
   */
  @Bean
  @ConditionalOnMissingBean(GestorConsumo.class)
  public GestorConsumo gestorConsumo(
      RabbitTemplate rabbitTemplate, MensajeriaProperties propiedades) {
    return new GestorConsumo(rabbitTemplate, propiedades);
  }
}
