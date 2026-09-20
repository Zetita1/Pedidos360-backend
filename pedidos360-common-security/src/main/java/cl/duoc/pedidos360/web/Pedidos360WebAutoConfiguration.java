package cl.duoc.pedidos360.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** Publica el manejo de errores comun a todos los microservicios de Pedidos360. */
@AutoConfiguration
@ConditionalOnWebApplication
public class Pedidos360WebAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ApiExceptionHandler.class)
  public ApiExceptionHandler apiExceptionHandler() {
    return new ApiExceptionHandler();
  }

  @Bean
  @ConditionalOnMissingBean(CorrelationIdFilter.class)
  public CorrelationIdFilter correlationIdFilter() {
    return new CorrelationIdFilter();
  }
}
