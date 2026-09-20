package cl.duoc.pedidos360.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Backend For Frontend de Pedidos360: unica puerta de entrada del SPA Angular. Valida el JWT
 * emitido por el IDaaS igual que el API Gateway y orquesta a los microservicios de dominio.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BffApplication {

  public static void main(String[] args) {
    SpringApplication.run(BffApplication.class, args);
  }
}
