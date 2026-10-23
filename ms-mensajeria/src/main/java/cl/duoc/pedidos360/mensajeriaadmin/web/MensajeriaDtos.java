package cl.duoc.pedidos360.mensajeriaadmin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Contratos de entrada y salida del administrador de mensajeria.
 *
 * <p>Las anotaciones de validacion son la primera linea de defensa: sin ellas seria posible
 * crear una cola sin nombre, con espacios o con un tipo de exchange inexistente.
 */
public final class MensajeriaDtos {

  private MensajeriaDtos() {}

  /** Caracteres validos en un nombre de cola o exchange: letras, numeros, punto, guion y _ */
  public static final String PATRON_NOMBRE = "^[a-zA-Z0-9._-]+$";

  /** Tipos de exchange soportados por RabbitMQ. */
  public static final String PATRON_TIPO_EXCHANGE = "^(direct|topic|fanout|headers)$";

  /**
   * Peticion para crear una cola.
   *
   * @param nombre nombre de la cola
   * @param durable si sobrevive al reinicio del broker
   * @param exclusiva si solo la usa la conexion que la declara
   * @param autoEliminar si se borra al quedarse sin consumidores
   * @param conDlq si ademas se crea su cola de mensajes muertos
   * @param mensajeTtlMs tiempo de vida de los mensajes, en milisegundos
   * @param maxMensajes tope de mensajes en la cola
   */
  public record CrearColaRequest(
      @NotBlank(message = "El nombre de la cola es obligatorio")
          @Size(min = 3, max = 120, message = "El nombre debe tener entre 3 y 120 caracteres")
          @Pattern(
              regexp = PATRON_NOMBRE,
              message = "El nombre solo admite letras, numeros, punto, guion y guion bajo")
          String nombre,
      Boolean durable,
      Boolean exclusiva,
      Boolean autoEliminar,
      Boolean conDlq,
      @PositiveOrZero(message = "El TTL no puede ser negativo") Integer mensajeTtlMs,
      @PositiveOrZero(message = "El maximo de mensajes no puede ser negativo")
          Integer maxMensajes) {

    /** @return true si la cola debe ser durable (valor por defecto) */
    public boolean durableOPorDefecto() {
      return durable == null || durable;
    }

    /** @return true si la cola es exclusiva */
    public boolean exclusivaOPorDefecto() {
      return exclusiva != null && exclusiva;
    }

    /** @return true si la cola se auto elimina */
    public boolean autoEliminarOPorDefecto() {
      return autoEliminar != null && autoEliminar;
    }

    /** @return true si hay que crear ademas su DLQ */
    public boolean conDlqOPorDefecto() {
      return conDlq != null && conDlq;
    }
  }

  /**
   * Peticion para crear un exchange.
   *
   * @param nombre nombre del exchange
   * @param tipo tipo de exchange: direct, topic, fanout o headers
   * @param durable si sobrevive al reinicio del broker
   * @param autoEliminar si se borra al quedarse sin bindings
   */
  public record CrearExchangeRequest(
      @NotBlank(message = "El nombre del exchange es obligatorio")
          @Size(min = 3, max = 120, message = "El nombre debe tener entre 3 y 120 caracteres")
          @Pattern(
              regexp = PATRON_NOMBRE,
              message = "El nombre solo admite letras, numeros, punto, guion y guion bajo")
          String nombre,
      @NotBlank(message = "El tipo de exchange es obligatorio")
          @Pattern(
              regexp = PATRON_TIPO_EXCHANGE,
              message = "El tipo debe ser direct, topic, fanout o headers")
          String tipo,
      Boolean durable,
      Boolean autoEliminar) {

    /** @return true si el exchange debe ser durable (valor por defecto) */
    public boolean durableOPorDefecto() {
      return durable == null || durable;
    }

    /** @return true si el exchange se auto elimina */
    public boolean autoEliminarOPorDefecto() {
      return autoEliminar != null && autoEliminar;
    }
  }

  /**
   * Peticion para enlazar una cola a un exchange.
   *
   * @param cola cola destino
   * @param exchange exchange origen
   * @param routingKey patron de enrutamiento
   */
  public record BindingRequest(
      @NotBlank(message = "La cola es obligatoria")
          @Pattern(regexp = PATRON_NOMBRE, message = "Nombre de cola invalido")
          String cola,
      @NotBlank(message = "El exchange es obligatorio")
          @Pattern(regexp = PATRON_NOMBRE, message = "Nombre de exchange invalido")
          String exchange,
      @NotBlank(message = "La routing key es obligatoria")
          @Size(max = 255, message = "La routing key no puede superar 255 caracteres")
          String routingKey) {}

  /**
   * Estado de una cola.
   *
   * @param nombre nombre de la cola
   * @param mensajes mensajes pendientes
   * @param consumidores consumidores conectados
   * @param delSistema true si forma parte de la topologia de Pedidos360
   */
  public record ColaResponse(
      String nombre, Integer mensajes, Integer consumidores, boolean delSistema) {}

  /**
   * Exchange declarado en el broker.
   *
   * @param nombre nombre del exchange
   * @param tipo tipo de exchange
   * @param durable si sobrevive al reinicio
   * @param delSistema true si forma parte de la topologia de Pedidos360
   */
  public record ExchangeResponse(
      String nombre, String tipo, Boolean durable, boolean delSistema) {}

  /**
   * Resultado de una operacion de administracion.
   *
   * @param operacion accion ejecutada
   * @param recurso nombre del recurso afectado
   * @param detalle explicacion legible
   */
  public record OperacionResponse(String operacion, String recurso, String detalle) {}

  /**
   * Topologia declarada por la aplicacion, la que no deberia tocarse a mano.
   *
   * @param exchange exchange de eventos
   * @param exchangeDescarte exchange de descarte
   * @param maxIntentos intentos antes de la DLQ
   * @param esperaReintentoMs espera entre reintentos
   * @param rutas routing keys por evento
   * @param casos colas por caso de uso, con su DLQ y su cola de reintento
   */
  public record TopologiaResponse(
      String exchange,
      String exchangeDescarte,
      int maxIntentos,
      long esperaReintentoMs,
      List<RutaResponse> rutas,
      List<CasoDeUsoResponse> casos) {}

  /**
   * Routing key configurada para un evento.
   *
   * @param evento nombre logico del evento
   * @param routingKey routing key con la que se publica
   */
  public record RutaResponse(String evento, String routingKey) {}

  /**
   * Colas de un caso de uso.
   *
   * @param casoDeUso nombre logico del caso de uso
   * @param cola cola principal
   * @param colaReintento cola de espera entre reintentos
   * @param dlq cola de mensajes muertos
   */
  public record CasoDeUsoResponse(
      String casoDeUso, String cola, String colaReintento, String dlq) {}
}
