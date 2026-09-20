package cl.duoc.pedidos360.pedidos.client;

import cl.duoc.pedidos360.web.RecursoNoEncontradoException;
import cl.duoc.pedidos360.web.ReglaNegocioException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Implementacion HTTP del puerto hacia ms-productos, con reenvio del token del usuario. */
@Component
public class CatalogoRestClient implements CatalogoClient {

  private final RestClient restClient;

  public CatalogoRestClient(@Qualifier("restClientProductos") RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public ProductoInfo obtener(Long productoId) {
    return restClient
        .get()
        .uri("/api/v1/productos/{id}", productoId)
        .retrieve()
        .onStatus(
            status -> status.value() == HttpStatus.NOT_FOUND.value(),
            (request, response) -> {
              throw new RecursoNoEncontradoException("producto", productoId);
            })
        .body(ProductoInfo.class);
  }

  @Override
  public void descontarStock(Long productoId, int cantidad, String motivo) {
    movimiento("descontar", productoId, cantidad, motivo);
  }

  @Override
  public void reponerStock(Long productoId, int cantidad, String motivo) {
    movimiento("reponer", productoId, cantidad, motivo);
  }

  private void movimiento(String operacion, Long productoId, int cantidad, String motivo) {
    restClient
        .post()
        .uri("/api/v1/productos/{id}/stock/{operacion}", productoId, operacion)
        .body(Map.of("cantidad", cantidad, "motivo", motivo == null ? "" : motivo))
        .retrieve()
        .onStatus(
            status -> status.value() == HttpStatus.NOT_FOUND.value(),
            (request, response) -> {
              throw new RecursoNoEncontradoException("producto", productoId);
            })
        .onStatus(
            status -> status.value() == HttpStatus.CONFLICT.value(),
            (request, response) -> {
              throw new ReglaNegocioException(
                  "El catalogo rechazo el movimiento de stock del producto " + productoId);
            })
        .toBodilessEntity();
  }
}
