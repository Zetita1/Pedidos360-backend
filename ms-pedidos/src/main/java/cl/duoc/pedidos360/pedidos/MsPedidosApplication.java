package cl.duoc.pedidos360.pedidos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Microservicio de pedidos de Pedidos360. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MsPedidosApplication {

  public static void main(String[] args) {
    SpringApplication.run(MsPedidosApplication.class, args);
  }
}
