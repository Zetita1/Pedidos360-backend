package cl.duoc.pedidos360.clientes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Microservicio de clientes y direcciones de despacho de Pedidos360. */
@SpringBootApplication
public class MsClientesApplication {

  public static void main(String[] args) {
    SpringApplication.run(MsClientesApplication.class, args);
  }
}
