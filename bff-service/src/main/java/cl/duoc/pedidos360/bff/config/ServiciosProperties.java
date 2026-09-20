package cl.duoc.pedidos360.bff.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Ubicacion de los microservicios de dominio que orquesta el BFF. */
@ConfigurationProperties(prefix = "pedidos360.servicios")
public class ServiciosProperties {

  private String pedidosUrl = "http://localhost:8081";

  private String productosUrl = "http://localhost:8082";

  private Duration connectTimeout = Duration.ofSeconds(3);

  private Duration readTimeout = Duration.ofSeconds(8);

  public String getPedidosUrl() {
    return pedidosUrl;
  }

  public void setPedidosUrl(String pedidosUrl) {
    this.pedidosUrl = pedidosUrl;
  }

  public String getProductosUrl() {
    return productosUrl;
  }

  public void setProductosUrl(String productosUrl) {
    this.productosUrl = productosUrl;
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
