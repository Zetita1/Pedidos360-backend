package cl.duoc.pedidos360.mensajeria.config;

import cl.duoc.pedidos360.mensajeria.evento.PedidoConfirmadoEvento;
import cl.duoc.pedidos360.mensajeria.evento.PedidoCreadoEvento;
import cl.duoc.pedidos360.mensajeria.evento.PedidoEstadoCambiadoEvento;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara la topologia completa de RabbitMQ: un exchange de eventos, un exchange de descarte
 * y, por cada caso de uso, su cola principal, su cola de espera entre reintentos y su DLQ.
 *
 * <p>Cada ruta de mensajeria queda declarada en un bean con nombre propio, y todos los
 * nombres salen de {@link MensajeriaProperties}. Ninguna clase de negocio participa aqui.
 *
 * <pre>
 *   pedido.creado ─────────────┐
 *                              ├──► pedidos360.notificaciones ──► .retry ──► (vuelve) ──► .dlq
 *   pedido.estado-cambiado ────┘
 *   pedido.confirmado ─────────────► pedidos360.documentos ─────► .retry ──► (vuelve) ──► .dlq
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
public class TopologiaMensajeriaConfig {

  /** Caso de uso que recibe los avisos al cliente. */
  public static final String CASO_NOTIFICACIONES = "notificaciones";

  /** Caso de uso que genera los tickets de los pedidos confirmados. */
  public static final String CASO_DOCUMENTOS = "documentos";

  private final MensajeriaProperties propiedades;

  public TopologiaMensajeriaConfig(MensajeriaProperties propiedades) {
    this.propiedades = propiedades;
  }

  // ------------------------------------------------------------------ exchanges

  /**
   * Exchange principal por donde se publican los eventos de dominio.
   *
   * @return exchange de tipo topic, durable
   */
  @Bean
  public TopicExchange exchangeEventos() {
    return new TopicExchange(propiedades.getExchange(), true, false);
  }

  /**
   * Exchange de descarte: las colas principales derivan aqui lo que rechazan, y desde aqui
   * cada mensaje llega a la DLQ de su propia cola.
   *
   * @return exchange de tipo topic, durable
   */
  @Bean
  public TopicExchange exchangeDescarte() {
    return new TopicExchange(propiedades.getExchangeDescarte(), true, false);
  }

  // ------------------------------------------------- caso de uso: notificaciones

  /** Cola principal de notificaciones; lo que rechaza viaja al exchange de descarte. */
  @Bean
  public Queue colaNotificaciones() {
    return colaPrincipal(propiedades.cola(CASO_NOTIFICACIONES));
  }

  /** Cola de espera de notificaciones: retiene el mensaje y lo devuelve a la cola principal. */
  @Bean
  public Queue colaNotificacionesReintento() {
    return colaReintento(propiedades.cola(CASO_NOTIFICACIONES));
  }

  /** Cola de mensajes muertos de notificaciones. */
  @Bean
  public Queue colaNotificacionesDlq() {
    return colaDlq(propiedades.cola(CASO_NOTIFICACIONES));
  }

  /** Ruta: un pedido nuevo genera el aviso de recepcion. */
  @Bean
  public Binding bindingNotificacionesPedidoCreado() {
    return BindingBuilder.bind(colaNotificaciones())
        .to(exchangeEventos())
        .with(propiedades.ruta(PedidoCreadoEvento.NOMBRE));
  }

  /** Ruta: cada cambio de estado genera el aviso de seguimiento. */
  @Bean
  public Binding bindingNotificacionesEstadoCambiado() {
    return BindingBuilder.bind(colaNotificaciones())
        .to(exchangeEventos())
        .with(propiedades.ruta(PedidoEstadoCambiadoEvento.NOMBRE));
  }

  /** Ruta de descarte de notificaciones: del exchange de descarte a su DLQ. */
  @Bean
  public Binding bindingNotificacionesDlq() {
    return bindingDlq(colaNotificacionesDlq(), propiedades.cola(CASO_NOTIFICACIONES));
  }

  // ----------------------------------------------------- caso de uso: documentos

  /** Cola principal de documentos (tickets de pedidos confirmados). */
  @Bean
  public Queue colaDocumentos() {
    return colaPrincipal(propiedades.cola(CASO_DOCUMENTOS));
  }

  /** Cola de espera de documentos entre reintentos. */
  @Bean
  public Queue colaDocumentosReintento() {
    return colaReintento(propiedades.cola(CASO_DOCUMENTOS));
  }

  /** Cola de mensajes muertos de documentos. */
  @Bean
  public Queue colaDocumentosDlq() {
    return colaDlq(propiedades.cola(CASO_DOCUMENTOS));
  }

  /** Ruta: un pedido confirmado genera su ticket. */
  @Bean
  public Binding bindingDocumentosPedidoConfirmado() {
    return BindingBuilder.bind(colaDocumentos())
        .to(exchangeEventos())
        .with(propiedades.ruta(PedidoConfirmadoEvento.NOMBRE));
  }

  /** Ruta de descarte de documentos: del exchange de descarte a su DLQ. */
  @Bean
  public Binding bindingDocumentosDlq() {
    return bindingDlq(colaDocumentosDlq(), propiedades.cola(CASO_DOCUMENTOS));
  }

  // ----------------------------------------------------------------- utilidades

  /**
   * Cola principal de un caso de uso. Lo que el consumidor rechaza sin reencolar sale al
   * exchange de descarte con la routing key de su propia DLQ.
   *
   * @param cola nombre de la cola
   * @return definicion durable de la cola
   */
  private Queue colaPrincipal(String cola) {
    return QueueBuilder.durable(cola)
        .deadLetterExchange(propiedades.getExchangeDescarte())
        .deadLetterRoutingKey(propiedades.dlqDe(cola))
        .build();
  }

  /**
   * Cola de espera entre reintentos: retiene el mensaje el tiempo configurado y lo devuelve
   * a la cola principal usando el exchange por defecto, que enruta por nombre de cola.
   *
   * @param cola nombre de la cola principal
   * @return definicion durable de la cola de reintento
   */
  private Queue colaReintento(String cola) {
    return QueueBuilder.durable(propiedades.reintentoDe(cola))
        .ttl((int) propiedades.getEsperaReintentoMs())
        .deadLetterExchange("")
        .deadLetterRoutingKey(cola)
        .build();
  }

  /**
   * Cola de mensajes muertos: punto final de los eventos que no se pudieron procesar.
   *
   * @param cola nombre de la cola principal
   * @return definicion durable de la DLQ
   */
  private Queue colaDlq(String cola) {
    return QueueBuilder.durable(propiedades.dlqDe(cola))
        .withArguments(Map.of("x-queue-type", "classic"))
        .build();
  }

  /**
   * Enlaza una DLQ al exchange de descarte.
   *
   * @param dlq cola de mensajes muertos
   * @param cola nombre de la cola principal
   * @return binding entre el exchange de descarte y la DLQ
   */
  private Binding bindingDlq(Queue dlq, String cola) {
    return BindingBuilder.bind(dlq).to(exchangeDescarte()).with(propiedades.dlqDe(cola));
  }
}
