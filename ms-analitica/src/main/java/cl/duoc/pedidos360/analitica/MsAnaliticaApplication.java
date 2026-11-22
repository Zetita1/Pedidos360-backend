package cl.duoc.pedidos360.analitica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Microservicio de analitica: consume los topicos de Kafka y mantiene las proyecciones que
 * alimentan el tablero.
 *
 * <p>No expone escrituras. Todo lo que publica por REST salio de un registro de un topico,
 * nunca de una peticion del usuario.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MsAnaliticaApplication {

  /**
   * @param args argumentos de la linea de comandos
   */
  public static void main(String[] args) {
    SpringApplication.run(MsAnaliticaApplication.class, args);
  }
}
