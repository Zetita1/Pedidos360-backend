package cl.duoc.pedidos360.pedidos.client;

import cl.duoc.pedidos360.web.RecursoNoEncontradoException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Implementacion HTTP del puerto hacia ms-clientes, con reenvio del token del usuario. */
@Component
public class ClientesRestClient implements ClientesClient {

  private final RestClient restClient;

  public ClientesRestClient(@Qualifier("restClientClientes") RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public ClienteInfo obtener(Long clienteId) {
    return restClient
        .get()
        .uri("/api/v1/clientes/{id}", clienteId)
        .retrieve()
        .onStatus(
            status -> status.value() == HttpStatus.NOT_FOUND.value(),
            (request, response) -> {
              throw new RecursoNoEncontradoException("cliente", clienteId);
            })
        .body(ClienteInfo.class);
  }
}
