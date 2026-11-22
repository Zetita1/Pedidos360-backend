package cl.duoc.pedidos360.streaming.evento;

import java.time.Instant;

/**
 * Contrato comun de los registros que viajan por los topicos de Kafka.
 *
 * <p>Obliga a que todo evento traiga tres cosas: un identificador propio para descartar
 * duplicados, el momento del hecho para poder agrupar por ventana de tiempo, y la clave de
 * particionado, que es lo que decide en que particion cae y con que otros registros mantiene
 * el orden.
 */
public interface EventoStream {

  /**
   * Identificador unico del registro.
   *
   * <p>Kafka garantiza entrega al menos una vez: ante un reequilibrio o un reintento, el
   * mismo registro puede llegar dos veces. Los consumidores guardan este id y descartan el
   * repetido, de modo que procesar dos veces no duplica el efecto.
   *
   * @return identificador del evento
   */
  String eventoId();

  /**
   * Momento en que ocurrio el hecho de negocio.
   *
   * <p>Es el instante del hecho, no el de la publicacion: la analitica agrupa por cuando
   * paso, no por cuando se leyo.
   *
   * @return momento del hecho
   */
  Instant ocurridoEn();

  /**
   * Clave de particionado del registro.
   *
   * <p>Kafka solo garantiza orden dentro de una particion, y la particion se elige por el
   * hash de la clave. Elegirla bien es la decision de diseno mas importante del topico: todos
   * los registros que deban leerse en orden tienen que compartir clave.
   *
   * @return clave con la que se publica el registro
   */
  String clave();
}
