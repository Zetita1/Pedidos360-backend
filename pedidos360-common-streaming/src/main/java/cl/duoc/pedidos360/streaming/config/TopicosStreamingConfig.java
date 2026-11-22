package cl.duoc.pedidos360.streaming.config;

import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;

/**
 * Declaracion de los topicos del sistema con sus particiones, replicas y politicas.
 *
 * <p>{@code KafkaAdmin} aplica estos beans al arrancar, de modo que la topologia es parte del
 * codigo y no de un paso manual: levantar el cluster y arrancar los microservicios deja los
 * topicos como aqui se describen.
 *
 * <p>Los dos flujos comparten particiones y replicas, pero no la politica de limpieza, porque
 * no guardan lo mismo:
 *
 * <ul>
 *   <li><b>Hechos de pedidos</b>: historia. Importa la secuencia completa, asi que se borra
 *       por tiempo ({@code delete}) y se conserva una semana.
 *   <li><b>Stock por producto</b>: estado. Importa el ultimo valor de cada producto, asi que
 *       se compacta ({@code compact}) y Kafka conserva ese ultimo registro por clave
 *       indefinidamente. El topico queda como una tabla de estado reconstruible.
 * </ul>
 *
 * <p>Nota operativa: {@code KafkaAdmin} crea el topico que falta y puede agregar particiones,
 * pero no reescribe la configuracion de un topico que ya existe. Para cambiar una retencion
 * ya aplicada se usa {@code infra/kafka/crear-topicos.sh} o el microservicio administrador.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "pedidos360.streaming",
    name = "declarar-topicos",
    matchIfMissing = true)
public class TopicosStreamingConfig {

  private final StreamingProperties propiedades;

  public TopicosStreamingConfig(StreamingProperties propiedades) {
    this.propiedades = propiedades;
  }

  /**
   * Flujo de hechos de pedidos.
   *
   * <p>Se particiona por numero de pedido: todos los eventos de un mismo pedido caen en la
   * misma particion y se leen en el orden en que ocurrieron, que es la unica garantia de
   * orden que da Kafka. Entre pedidos distintos el orden no importa, y por eso se puede
   * procesar en paralelo.
   *
   * @return declaracion del topico de pedidos
   */
  @Bean
  public NewTopic topicoPedidos() {
    return TopicBuilder.name(propiedades.topico("pedidos"))
        .partitions(propiedades.getParticiones())
        .replicas(propiedades.getReplicas())
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
        .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(propiedades.getRetencionPedidosMs()))
        .config(
            TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
            String.valueOf(propiedades.getReplicasEnSincronia()))
        .build();
  }

  /**
   * Estado de stock por producto.
   *
   * <p>Se particiona por id de producto y se compacta, asi que basta leer el topico desde el
   * principio para reconstruir el stock vigente de todo el catalogo sin consultar la base de
   * datos.
   *
   * <p>{@code segment.ms} y {@code min.cleanable.dirty.ratio} se dejan bajos para que la
   * compactacion ocurra pronto y pueda observarse; en un sistema con volumen real se
   * subirian para no compactar de mas.
   *
   * @return declaracion del topico de stock
   */
  @Bean
  public NewTopic topicoStock() {
    return TopicBuilder.name(propiedades.topico("stock"))
        .partitions(propiedades.getParticiones())
        .replicas(propiedades.getReplicas())
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
        .config(
            TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
            String.valueOf(propiedades.getReplicasEnSincronia()))
        .config(TopicConfig.SEGMENT_MS_CONFIG, "60000")
        .config(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.1")
        .config(TopicConfig.DELETE_RETENTION_MS_CONFIG, "120000")
        .build();
  }

  /**
   * Descarte del flujo de pedidos: lo que ningun reintento logro procesar.
   *
   * @return declaracion del DLT de pedidos
   */
  @Bean
  public NewTopic topicoPedidosDescarte() {
    return topicoDescarte(propiedades.dltDe(propiedades.topico("pedidos")));
  }

  /**
   * Descarte del flujo de stock.
   *
   * @return declaracion del DLT de stock
   */
  @Bean
  public NewTopic topicoStockDescarte() {
    return topicoDescarte(propiedades.dltDe(propiedades.topico("stock")));
  }

  /**
   * Construye un topico de descarte.
   *
   * <p>Mantiene las mismas particiones que su topico de origen, porque el registro se
   * republica conservando su numero de particion: asi los descartes de una misma clave
   * siguen juntos y es posible reprocesarlos en orden.
   *
   * @param nombre nombre del topico de descarte
   * @return declaracion del topico
   */
  private NewTopic topicoDescarte(String nombre) {
    return TopicBuilder.name(nombre)
        .partitions(propiedades.getParticiones())
        .replicas(propiedades.getReplicas())
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
        .config(
            TopicConfig.RETENTION_MS_CONFIG, String.valueOf(propiedades.getRetencionDescarteMs()))
        .config(
            TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
            String.valueOf(propiedades.getReplicasEnSincronia()))
        .build();
  }
}
