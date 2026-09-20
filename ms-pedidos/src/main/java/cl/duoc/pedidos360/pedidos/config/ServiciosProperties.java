package cl.duoc.pedidos360.pedidos.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Ubicacion de los microservicios de dominio que consume el servicio de pedidos. */
@ConfigurationProperties(prefix = "pedidos360.servicios")
public class ServiciosProperties {

  /** URL base del microservicio de productos. */
  private String productosUrl = "http://localhost:8082";

  /** URL base del microservicio de clientes. */
  private String clientesUrl = "http://localhost:8083";

  private Duration connectTimeout = Duration.ofSeconds(3);

  private Duration readTimeout = Duration.ofSeconds(8);

  public String getProductosUrl() {
    return productosUrl;
  }

  public void setProductosUrl(String productosUrl) {
    this.productosUrl = productosUrl;
  }

  public String getClientesUrl() {
    return clientesUrl;
  }

  public void setClientesUrl(String clientesUrl) {
    this.clientesUrl = clientesUrl;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }
}
