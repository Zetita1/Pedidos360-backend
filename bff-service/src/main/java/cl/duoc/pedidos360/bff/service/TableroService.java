package cl.duoc.pedidos360.bff.service;

import cl.duoc.pedidos360.bff.client.DominioClient;
import cl.duoc.pedidos360.bff.model.BffModels.PedidoResumen;
import cl.duoc.pedidos360.bff.model.BffModels.ProductoCritico;
import cl.duoc.pedidos360.bff.model.BffModels.Tablero;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Arma el tablero de inicio agregando datos de pedidos y catalogo en una sola respuesta,
 * evitando que el SPA haga varias llamadas a traves del API Gateway.
 */
@Service
public class TableroService {

  /** Umbral bajo el cual se considera que un producto necesita reposicion. */
  private static final int STOCK_CRITICO = 10;

  private static final int MAXIMO_ULTIMOS_PEDIDOS = 5;

  private final DominioClient dominio;

  public TableroService(DominioClient dominio) {
    this.dominio = dominio;
  }

  /**
   * Construye el tablero con el resumen por estado, el monto en curso, el stock critico
   * y los ultimos pedidos.
   *
   * @return datos agregados del tablero
   */
  public Tablero construir() {
    Map<String, Long> porEstado = new LinkedHashMap<>();
    long totalPedidos = 0;

    JsonNode resumen = cuerpo(dominio.pedidos(HttpMethod.GET, "/api/v1/pedidos/resumen"));
    if (resumen != null && resumen.isArray()) {
      for (JsonNode fila : resumen) {
        long cantidad = fila.path("cantidad").asLong();
        porEstado.put(fila.path("estado").asText(), cantidad);
        totalPedidos += cantidad;
      }
    }

    List<PedidoResumen> ultimos = new ArrayList<>();
    BigDecimal montoEnCurso = BigDecimal.ZERO;

    JsonNode pedidos = cuerpo(dominio.pedidos(HttpMethod.GET, "/api/v1/pedidos"));
    if (pedidos != null && pedidos.isArray()) {
      for (JsonNode pedido : pedidos) {
        String estado = pedido.path("estado").asText();
        if (!"ANULADO".equals(estado) && !"BORRADOR".equals(estado)) {
          montoEnCurso = montoEnCurso.add(pedido.path("total").decimalValue());
        }
        if (ultimos.size() < MAXIMO_ULTIMOS_PEDIDOS) {
          ultimos.add(
              new PedidoResumen(
                  pedido.path("id").asLong(),
                  pedido.path("numero").asText(),
                  pedido.path("clienteRazonSocial").asText(),
                  estado,
                  pedido.path("total").decimalValue(),
                  pedido.path("creadoEn").asText(null)));
        }
      }
    }

    List<ProductoCritico> stockCritico = new ArrayList<>();
    long productosActivos = 0;

    JsonNode productos = cuerpo(dominio.productos(HttpMethod.GET, "/api/v1/productos"));
    if (productos != null && productos.isArray()) {
      for (JsonNode producto : productos) {
        productosActivos++;
        int stock = producto.path("stock").asInt();
        if (stock <= STOCK_CRITICO) {
          stockCritico.add(
              new ProductoCritico(
                  producto.path("id").asLong(),
                  producto.path("sku").asText(),
                  producto.path("nombre").asText(),
                  stock,
                  producto.path("precio").decimalValue()));
        }
      }
    }

    return new Tablero(
        totalPedidos, porEstado, montoEnCurso, productosActivos, stockCritico, ultimos);
  }

  /**
   * Devuelve el cuerpo solo cuando la llamada aguas abajo fue exitosa; si un microservicio
   * responde con error, el tablero se arma con la informacion disponible en vez de caerse.
   */
  private JsonNode cuerpo(ResponseEntity<JsonNode> respuesta) {
    return respuesta.getStatusCode().is2xxSuccessful() ? respuesta.getBody() : null;
  }
}
