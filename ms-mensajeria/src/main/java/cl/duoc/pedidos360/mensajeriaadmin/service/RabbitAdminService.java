package cl.duoc.pedidos360.mensajeriaadmin.service;

import cl.duoc.pedidos360.mensajeria.config.MensajeriaProperties;
import cl.duoc.pedidos360.mensajeriaadmin.config.AdminMensajeriaProperties;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.BindingRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.CasoDeUsoResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.ColaResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.CrearColaRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.CrearExchangeRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.ExchangeResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.RutaResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.TopologiaResponse;
import cl.duoc.pedidos360.web.RecursoNoEncontradoException;
import cl.duoc.pedidos360.web.ReglaNegocioException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

/**
 * Toda la conversacion con RabbitMQ para administrar su topologia.
 *
 * <p>Los controladores llaman metodos de alto nivel ({@code crearCola}, {@code eliminarCola})
 * y no saben nada de {@code RabbitAdmin}, de argumentos {@code x-dead-letter-exchange} ni de
 * la API de gestion: ese detalle vive solo aqui.
 */
@Service
public class RabbitAdminService {

  private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

  private final RabbitAdmin rabbitAdmin;
  private final MensajeriaProperties topologia;
  private final AdminMensajeriaProperties propiedades;
  private final ClienteGestionRabbit clienteGestion;

  public RabbitAdminService(
      RabbitAdmin rabbitAdmin,
      MensajeriaProperties topologia,
      AdminMensajeriaProperties propiedades,
      ClienteGestionRabbit clienteGestion) {
    this.rabbitAdmin = rabbitAdmin;
    this.topologia = topologia;
    this.propiedades = propiedades;
    this.clienteGestion = clienteGestion;
  }

  // --------------------------------------------------------------------- colas

  /**
   * Lista las colas del broker. Si el plugin de gestion no responde, cae en la topologia
   * declarada por la aplicacion y consulta cada cola por AMQP.
   *
   * @return colas con su cantidad de mensajes y consumidores
   */
  public List<ColaResponse> listarColas() {
    List<ColaResponse> desdeGestion = clienteGestion.listarColas(this::esDelSistema);
    if (!desdeGestion.isEmpty()) {
      return desdeGestion;
    }
    log.debug("API de gestion no disponible: se listan las colas declaradas por la aplicacion");
    return colasDelSistema().stream().map(this::describirCola).sorted(porNombre()).toList();
  }

  /**
   * Estado de una cola puntual.
   *
   * @param nombre nombre de la cola
   * @return mensajes y consumidores de la cola
   */
  public ColaResponse obtenerCola(String nombre) {
    QueueInformation info = informacion(nombre);
    if (info == null) {
      throw new RecursoNoEncontradoException("La cola " + nombre + " no existe en el broker");
    }
    return new ColaResponse(
        info.getName(), info.getMessageCount(), info.getConsumerCount(), esDelSistema(nombre));
  }

  /**
   * Crea una cola y, si se pide, tambien su DLQ enlazada al exchange de descarte.
   *
   * @param peticion datos de la cola
   * @return cola creada
   */
  public ColaResponse crearCola(CrearColaRequest peticion) {
    String nombre = peticion.nombre().trim();
    if (informacion(nombre) != null) {
      throw new ReglaNegocioException("La cola " + nombre + " ya existe");
    }

    QueueBuilder constructor =
        peticion.durableOPorDefecto() ? QueueBuilder.durable(nombre) : QueueBuilder.nonDurable(nombre);
    if (peticion.exclusivaOPorDefecto()) {
      constructor.exclusive();
    }
    if (peticion.autoEliminarOPorDefecto()) {
      constructor.autoDelete();
    }
    if (peticion.mensajeTtlMs() != null && peticion.mensajeTtlMs() > 0) {
      constructor.ttl(peticion.mensajeTtlMs());
    }
    if (peticion.maxMensajes() != null && peticion.maxMensajes() > 0) {
      constructor.maxLength(peticion.maxMensajes());
    }
    if (peticion.conDlqOPorDefecto()) {
      constructor
          .deadLetterExchange(topologia.getExchangeDescarte())
          .deadLetterRoutingKey(topologia.dlqDe(nombre));
    }

    declarar(constructor.build(), nombre);

    if (peticion.conDlqOPorDefecto()) {
      String dlq = topologia.dlqDe(nombre);
      declarar(QueueBuilder.durable(dlq).build(), dlq);
      enlazar(
          BindingBuilder.bind(new Queue(dlq))
              .to(new org.springframework.amqp.core.TopicExchange(topologia.getExchangeDescarte()))
              .with(dlq));
      log.info("Cola {} creada junto a su DLQ {}", nombre, dlq);
    } else {
      log.info("Cola {} creada", nombre);
    }
    return describirCola(nombre);
  }

  /**
   * Elimina una cola.
   *
   * @param nombre nombre de la cola
   * @param soloSiNoSeUsa elimina solo si no tiene consumidores
   * @param soloSiVacia elimina solo si no tiene mensajes
   */
  public void eliminarCola(String nombre, boolean soloSiNoSeUsa, boolean soloSiVacia) {
    exigirQueNoSeaDelSistema(nombre, "cola");
    if (informacion(nombre) == null) {
      throw new RecursoNoEncontradoException("La cola " + nombre + " no existe en el broker");
    }
    try {
      rabbitAdmin.deleteQueue(nombre, soloSiNoSeUsa, soloSiVacia);
      log.info(
          "Cola {} eliminada (soloSiNoSeUsa={}, soloSiVacia={})",
          nombre,
          soloSiNoSeUsa,
          soloSiVacia);
    } catch (AmqpException e) {
      throw new ReglaNegocioException(
          "No se pudo eliminar la cola " + nombre + ": " + mensaje(e));
    }
  }

  /**
   * Vacia una cola sin eliminarla.
   *
   * @param nombre nombre de la cola
   * @return cola ya purgada
   */
  public ColaResponse purgarCola(String nombre) {
    if (informacion(nombre) == null) {
      throw new RecursoNoEncontradoException("La cola " + nombre + " no existe en el broker");
    }
    try {
      rabbitAdmin.purgeQueue(nombre);
      log.info("Cola {} purgada", nombre);
      return describirCola(nombre);
    } catch (AmqpException e) {
      throw new ReglaNegocioException("No se pudo purgar la cola " + nombre + ": " + mensaje(e));
    }
  }

  // ----------------------------------------------------------------- exchanges

  /**
   * Lista los exchanges del broker consultando la API de gestion.
   *
   * @return exchanges declarados
   */
  public List<ExchangeResponse> listarExchanges() {
    return clienteGestion.listarExchanges(this::esDelSistema);
  }

  /**
   * Crea un exchange.
   *
   * @param peticion datos del exchange
   * @return exchange creado
   */
  public ExchangeResponse crearExchange(CrearExchangeRequest peticion) {
    String nombre = peticion.nombre().trim();
    ExchangeBuilder constructor =
        switch (peticion.tipo()) {
          case "direct" -> ExchangeBuilder.directExchange(nombre);
          case "topic" -> ExchangeBuilder.topicExchange(nombre);
          case "fanout" -> ExchangeBuilder.fanoutExchange(nombre);
          case "headers" -> ExchangeBuilder.headersExchange(nombre);
          default ->
              throw new ReglaNegocioException("Tipo de exchange no soportado: " + peticion.tipo());
        };
    if (peticion.durableOPorDefecto()) {
      constructor.durable(true);
    }
    if (peticion.autoEliminarOPorDefecto()) {
      constructor.autoDelete();
    }

    try {
      rabbitAdmin.declareExchange(constructor.build());
    } catch (AmqpException e) {
      throw new ReglaNegocioException(
          "No se pudo crear el exchange " + nombre + ": " + mensaje(e));
    }
    log.info("Exchange {} de tipo {} creado", nombre, peticion.tipo());
    return new ExchangeResponse(
        nombre, peticion.tipo(), peticion.durableOPorDefecto(), esDelSistema(nombre));
  }

  /**
   * Elimina un exchange.
   *
   * @param nombre nombre del exchange
   */
  public void eliminarExchange(String nombre) {
    exigirQueNoSeaDelSistema(nombre, "exchange");
    boolean eliminado;
    try {
      eliminado = rabbitAdmin.deleteExchange(nombre);
    } catch (AmqpException e) {
      throw new ReglaNegocioException(
          "No se pudo eliminar el exchange " + nombre + ": " + mensaje(e));
    }
    if (!eliminado) {
      throw new RecursoNoEncontradoException("El exchange " + nombre + " no existe en el broker");
    }
    log.info("Exchange {} eliminado", nombre);
  }

  // ------------------------------------------------------------------ bindings

  /**
   * Enlaza una cola a un exchange con una routing key.
   *
   * @param peticion datos del binding
   */
  public void crearBinding(BindingRequest peticion) {
    exigirQueExistan(peticion);
    enlazar(binding(peticion));
    log.info(
        "Binding creado: {} -> {} con routing key {}",
        peticion.exchange(),
        peticion.cola(),
        peticion.routingKey());
  }

  /**
   * Quita el enlace entre una cola y un exchange.
   *
   * @param peticion datos del binding
   */
  public void eliminarBinding(BindingRequest peticion) {
    if (esDelSistema(peticion.cola()) && propiedades.isProtegerTopologiaDelSistema()) {
      throw new ReglaNegocioException(
          "La cola "
              + peticion.cola()
              + " es parte de la topologia del sistema: sus bindings no se eliminan desde el API");
    }
    exigirQueExistan(peticion);
    try {
      rabbitAdmin.removeBinding(binding(peticion));
    } catch (AmqpException e) {
      throw new ReglaNegocioException("No se pudo eliminar el binding: " + mensaje(e));
    }
    log.info(
        "Binding eliminado: {} -> {} con routing key {}",
        peticion.exchange(),
        peticion.cola(),
        peticion.routingKey());
  }

  // ----------------------------------------------------------------- topologia

  /**
   * Topologia declarada por la aplicacion, tal como quedo configurada.
   *
   * @return exchanges, rutas y colas por caso de uso
   */
  public TopologiaResponse topologiaDelSistema() {
    List<RutaResponse> rutas =
        topologia.getRutas().entrySet().stream()
            .map(entrada -> new RutaResponse(entrada.getKey(), entrada.getValue()))
            .sorted(Comparator.comparing(RutaResponse::evento))
            .toList();

    List<CasoDeUsoResponse> casos =
        topologia.getColas().entrySet().stream()
            .map(
                entrada ->
                    new CasoDeUsoResponse(
                        entrada.getKey(),
                        entrada.getValue(),
                        topologia.reintentoDe(entrada.getValue()),
                        topologia.dlqDe(entrada.getValue())))
            .sorted(Comparator.comparing(CasoDeUsoResponse::casoDeUso))
            .toList();

    return new TopologiaResponse(
        topologia.getExchange(),
        topologia.getExchangeDescarte(),
        topologia.getMaxIntentos(),
        topologia.getEsperaReintentoMs(),
        rutas,
        casos);
  }

  // ----------------------------------------------------------------- auxiliares

  /**
   * Nombres de colas y exchanges que declara la aplicacion.
   *
   * @param nombre recurso a evaluar
   * @return true si forma parte de la topologia del sistema
   */
  public boolean esDelSistema(String nombre) {
    return nombresDelSistema().contains(nombre);
  }

  private Set<String> nombresDelSistema() {
    Set<String> nombres = new LinkedHashSet<>();
    nombres.add(topologia.getExchange());
    nombres.add(topologia.getExchangeDescarte());
    nombres.addAll(colasDelSistema());
    return nombres;
  }

  private List<String> colasDelSistema() {
    List<String> colas = new ArrayList<>();
    for (String cola : topologia.getColas().values()) {
      colas.add(cola);
      colas.add(topologia.reintentoDe(cola));
      colas.add(topologia.dlqDe(cola));
    }
    return colas;
  }

  private void exigirQueNoSeaDelSistema(String nombre, String tipo) {
    if (propiedades.isProtegerTopologiaDelSistema() && esDelSistema(nombre)) {
      throw new ReglaNegocioException(
          "El "
              + tipo
              + " "
              + nombre
              + " forma parte de la topologia de Pedidos360 y no se elimina desde el API");
    }
  }

  private void exigirQueExistan(BindingRequest peticion) {
    if (informacion(peticion.cola()) == null) {
      throw new RecursoNoEncontradoException(
          "La cola " + peticion.cola() + " no existe en el broker");
    }
  }

  private Binding binding(BindingRequest peticion) {
    return new Binding(
        peticion.cola(),
        Binding.DestinationType.QUEUE,
        peticion.exchange(),
        peticion.routingKey(),
        new HashMap<>());
  }

  private void declarar(Queue cola, String nombre) {
    try {
      String declarada = rabbitAdmin.declareQueue(cola);
      if (declarada == null) {
        throw new ReglaNegocioException(
            "El broker rechazo la creacion de la cola " + nombre);
      }
    } catch (AmqpException e) {
      throw new ReglaNegocioException("No se pudo crear la cola " + nombre + ": " + mensaje(e));
    }
  }

  private void enlazar(Binding binding) {
    try {
      rabbitAdmin.declareBinding(binding);
    } catch (AmqpException e) {
      throw new ReglaNegocioException("No se pudo crear el binding: " + mensaje(e));
    }
  }

  private ColaResponse describirCola(String nombre) {
    QueueInformation info = informacion(nombre);
    if (info == null) {
      return new ColaResponse(nombre, null, null, esDelSistema(nombre));
    }
    return new ColaResponse(
        info.getName(), info.getMessageCount(), info.getConsumerCount(), esDelSistema(nombre));
  }

  private QueueInformation informacion(String nombre) {
    try {
      return rabbitAdmin.getQueueInfo(nombre);
    } catch (AmqpException e) {
      log.warn("No fue posible consultar la cola {}: {}", nombre, mensaje(e));
      return null;
    }
  }

  private Comparator<ColaResponse> porNombre() {
    return Comparator.comparing(ColaResponse::nombre);
  }

  private String mensaje(Throwable error) {
    Throwable causa = error;
    while (causa.getCause() != null) {
      causa = causa.getCause();
    }
    return causa.getMessage() == null ? error.toString() : causa.getMessage();
  }

  /** Vista de solo lectura de la configuracion, util para las pruebas y los controladores. */
  public Map<String, String> colasPorCasoDeUso() {
    return Map.copyOf(topologia.getColas());
  }
}
