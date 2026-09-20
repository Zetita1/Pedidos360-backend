package cl.duoc.pedidos360.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Acceso del BFF a los microservicios de dominio para armar las vistas agregadas.
 *
 * <p>Devuelve {@link ResponseEntity} con el cuerpo sin transformar y sin lanzar excepcion
 * ante 4xx/5xx, de modo que el BFF pueda decidir que hacer con cada respuesta en vez de
 * enmascararla con un 500.
 */
@Component
public class DominioClient {

  private final RestClient pedidos;
  private final RestClient productos;

  public DominioClient(
      @Qualifier("restClientPedidos") RestClient pedidos,
      @Qualifier("restClientProductos") RestClient productos) {
    this.pedidos = pedidos;
    this.productos = productos;
  }

  /**
   * Llama al microservicio de pedidos reenviando el token del usuario.
   *
   * @param metodo verbo HTTP
   * @param path ruta dentro del microservicio
   * @return respuesta con su estado original
   */
  public ResponseEntity<JsonNode> pedidos(HttpMethod metodo, String path) {
    return llamar(pedidos, metodo, path);
  }

  /**
   * Llama al microservicio de productos reenviando el token del usuario.
   *
   * @param metodo verbo HTTP
   * @param path ruta dentro del microservicio
   * @return respuesta con su estado original
   */
  public ResponseEntity<JsonNode> productos(HttpMethod metodo, String path) {
    return llamar(productos, metodo, path);
  }

  private ResponseEntity<JsonNode> llamar(RestClient client, HttpMethod metodo, String path) {
    return client
        .method(metodo)
        .uri(path)
        .retrieve()
        .onStatus(status -> true, (request, response) -> { /* el estado se evalua arriba */ })
        .toEntity(JsonNode.class);
  }
}
