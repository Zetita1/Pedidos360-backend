package cl.duoc.pedidos360.mensajeriaadmin.service;

import cl.duoc.pedidos360.mensajeriaadmin.config.AdminMensajeriaProperties;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.ColaResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.ExchangeResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Lectura del inventario del broker a traves del plugin de gestion de RabbitMQ.
 *
 * <p>AMQP permite declarar y borrar, pero no listar: para eso existe la API HTTP de gestion.
 * Si el plugin no esta habilitado, este cliente devuelve listas vacias y quien lo llama cae
 * en la topologia declarada por la aplicacion.
 */
@Component
public class ClienteGestionRabbit {

  private static final Logger log = LoggerFactory.getLogger(ClienteGestionRabbit.class);

  private final RestClient restClient;
  private final String virtualHost;

  public ClienteGestionRabbit(
      RestClient.Builder builder, AdminMensajeriaProperties propiedades) {
    this.restClient =
        builder
            .baseUrl(propiedades.getUrlGestion())
            .defaultHeaders(
                cabeceras -> cabeceras.setBasicAuth(propiedades.getUsuario(), propiedades.getClave()))
            .build();
    this.virtualHost = URLEncoder.encode(propiedades.getVirtualHost(), StandardCharsets.UTF_8);
  }

  /**
   * Colas declaradas en el broker.
   *
   * @param esDelSistema permite marcar las colas propias de Pedidos360
   * @return colas con mensajes y consumidores, o lista vacia si la API no responde
   */
  public List<ColaResponse> listarColas(Predicate<String> esDelSistema) {
    List<Map<String, Object>> respuesta = consultar("/api/queues/" + virtualHost);
    return respuesta.stream()
        .map(
            cola -> {
              String nombre = texto(cola.get("name"));
              return new ColaResponse(
                  nombre,
                  entero(cola.get("messages")),
                  entero(cola.get("consumers")),
                  esDelSistema.test(nombre));
            })
        .sorted(Comparator.comparing(ColaResponse::nombre))
        .toList();
  }

  /**
   * Exchanges declarados en el broker, omitiendo los internos de RabbitMQ.
   *
   * @param esDelSistema permite marcar los exchanges propios de Pedidos360
   * @return exchanges, o lista vacia si la API no responde
   */
  public List<ExchangeResponse> listarExchanges(Predicate<String> esDelSistema) {
    List<Map<String, Object>> respuesta = consultar("/api/exchanges/" + virtualHost);
    return respuesta.stream()
        .map(
            exchange -> {
              String nombre = texto(exchange.get("name"));
              return new ExchangeResponse(
                  nombre,
                  texto(exchange.get("type")),
                  Boolean.TRUE.equals(exchange.get("durable")),
                  esDelSistema.test(nombre));
            })
        .filter(exchange -> !exchange.nombre().isBlank() && !exchange.nombre().startsWith("amq."))
        .sorted(Comparator.comparing(ExchangeResponse::nombre))
        .toList();
  }

  /**
   * Llama a la API de gestion y devuelve la lista cruda.
   *
   * @param ruta camino de la API, por ejemplo {@code /api/queues/%2F}
   * @return lista de mapas, vacia si la API no esta disponible
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> consultar(String ruta) {
    try {
      List<Map<String, Object>> cuerpo = restClient.get().uri(ruta).retrieve().body(List.class);
      return cuerpo == null ? List.of() : cuerpo;
    } catch (RestClientException e) {
      log.warn("API de gestion de RabbitMQ no disponible en {}: {}", ruta, e.getMessage());
      return List.of();
    }
  }

  private String texto(Object valor) {
    return valor == null ? "" : valor.toString();
  }

  private Integer entero(Object valor) {
    return valor instanceof Number numero ? numero.intValue() : null;
  }
}
