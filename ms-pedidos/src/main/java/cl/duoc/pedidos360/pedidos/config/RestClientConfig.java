package cl.duoc.pedidos360.pedidos.config;

import cl.duoc.pedidos360.security.TokenRelay;
import cl.duoc.pedidos360.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Construye los clientes HTTP hacia los microservicios de dominio. */
@Configuration
public class RestClientConfig {

  /**
   * Reenvia el Bearer token del usuario y el identificador de correlacion en cada llamada
   * interna, de modo que el servicio llamado vuelva a validar el JWT contra el IDaaS.
   *
   * @return interceptor de contexto
   */
  private ClientHttpRequestInterceptor interceptorDeContexto() {
    return (request, body, execution) -> {
      TokenRelay.tokenActual().ifPresent(token -> request.getHeaders().setBearerAuth(token));
      String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
      if (correlationId != null) {
        request.getHeaders().add(CorrelationIdFilter.HEADER, correlationId);
      }
      return execution.execute(request, body);
    };
  }

  private RestClient construir(String baseUrl, ServiciosProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
    factory.setReadTimeout((int) properties.getReadTimeout().toMillis());

    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.ACCEPT, "application/json")
        .requestInterceptor(interceptorDeContexto())
        .build();
  }

  @Bean("restClientProductos")
  public RestClient restClientProductos(ServiciosProperties properties) {
    return construir(properties.getProductosUrl(), properties);
  }

  @Bean("restClientClientes")
  public RestClient restClientClientes(ServiciosProperties properties) {
    return construir(properties.getClientesUrl(), properties);
  }
}
