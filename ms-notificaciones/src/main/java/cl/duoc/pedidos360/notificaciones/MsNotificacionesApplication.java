package cl.duoc.pedidos360.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de notificaciones y documentos de Pedidos360.
 *
 * <p>No expone escritura por HTTP: su trabajo entra por las colas de RabbitMQ y lo que
 * publica por REST es solo la consulta de lo ya generado.
 */
@SpringBootApplication
public class MsNotificacionesApplication {

  public static void main(String[] args) {
    SpringApplication.run(MsNotificacionesApplication.class, args);
  }
}
