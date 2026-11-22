package cl.duoc.pedidos360.mensajeriaadmin;

import cl.duoc.pedidos360.mensajeriaadmin.config.AdminMensajeriaProperties;
import cl.duoc.pedidos360.mensajeriaadmin.config.AdminStreamingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Microservicio administrador de RabbitMQ: expone por REST la creacion y eliminacion de
 * colas, exchanges y bindings, y la topologia declarada por la aplicacion.
 */
@SpringBootApplication
@EnableConfigurationProperties({AdminMensajeriaProperties.class, AdminStreamingProperties.class})
public class MsMensajeriaApplication {

  public static void main(String[] args) {
    SpringApplication.run(MsMensajeriaApplication.class, args);
  }
}
