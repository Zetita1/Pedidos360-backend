package cl.duoc.pedidos360.mensajeriaadmin.config;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/** Cliente administrativo de Kafka que usa el microservicio para operar el cluster. */
@Configuration(proxyBeanMethods = false)
public class KafkaAdminConfig {

  /**
   * Cliente administrativo compartido.
   *
   * <p>Se construye con la misma configuracion de conexion que el resto del sistema
   * ({@code spring.kafka}), de modo que apunta al mismo cluster y no hay dos definiciones de
   * donde esta Kafka.
   *
   * <p>Es un solo cliente para toda la aplicacion: mantiene sus conexiones abiertas y las
   * reutiliza. Crear uno por peticion abriria y cerraria conexiones a los tres brokers en
   * cada llamada del API.
   *
   * @param kafkaAdmin configuracion de conexion resuelta por Spring Boot
   * @return cliente administrativo, cerrado al apagar la aplicacion
   */
  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(Admin.class)
  public Admin adminKafka(KafkaAdmin kafkaAdmin) {
    return Admin.create(kafkaAdmin.getConfigurationProperties());
  }
}
