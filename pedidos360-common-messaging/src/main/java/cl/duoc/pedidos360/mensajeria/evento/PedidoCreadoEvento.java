package cl.duoc.pedidos360.mensajeria.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un pedido fue registrado en estado BORRADOR. Dispara la notificacion de recepcion al
 * cliente.
 *
 * @param eventoId identificador unico del evento
 * @param ocurridoEn momento del hecho
 * @param pedidoNumero correlativo del pedido
 * @param pedidoId identificador interno del pedido
 * @param clienteId cliente dueno del pedido
 * @param clienteNombre razon social o nombre del cliente
 * @param clienteEmail correo de contacto, destino de la notificacion
 * @param total monto total del pedido
 * @param lineas cantidad de lineas de detalle
 */
public record PedidoCreadoEvento(
    String eventoId,
    Instant ocurridoEn,
    String pedidoNumero,
    Long pedidoId,
    Long clienteId,
    String clienteNombre,
    String clienteEmail,
    BigDecimal total,
    int lineas)
    implements EventoPedidos360 {

  /** Nombre logico que enlaza el evento con su routing key configurada. */
  public static final String NOMBRE = "pedido-creado";

  /**
   * Crea el evento con identificador y marca de tiempo nuevos.
   *
   * @param pedidoNumero correlativo del pedido
   * @param pedidoId identificador interno del pedido
   * @param clienteId cliente dueno del pedido
   * @param clienteNombre nombre del cliente
   * @param clienteEmail correo de contacto
   * @param total monto total
   * @param lineas cantidad de lineas
   * @return evento listo para publicar
   */
  public static PedidoCreadoEvento de(
      String pedidoNumero,
      Long pedidoId,
      Long clienteId,
      String clienteNombre,
      String clienteEmail,
      BigDecimal total,
      int lineas) {
    return new PedidoCreadoEvento(
        UUID.randomUUID().toString(),
        Instant.now(),
        pedidoNumero,
        pedidoId,
        clienteId,
        clienteNombre,
        clienteEmail,
        total,
        lineas);
  }

  @Override
  public String nombreLogico() {
    return NOMBRE;
  }
}
