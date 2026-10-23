package cl.duoc.pedidos360.mensajeria.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un pedido paso a CONFIRMADO. Dispara la generacion del ticket del pedido y el aviso al
 * cliente.
 *
 * @param eventoId identificador unico del evento
 * @param ocurridoEn momento del hecho
 * @param pedidoNumero correlativo del pedido
 * @param pedidoId identificador interno del pedido
 * @param clienteId cliente dueno del pedido
 * @param clienteNombre nombre del cliente
 * @param clienteEmail correo de contacto
 * @param total monto total del pedido
 * @param detalles lineas del pedido para imprimir en el ticket
 */
public record PedidoConfirmadoEvento(
    String eventoId,
    Instant ocurridoEn,
    String pedidoNumero,
    Long pedidoId,
    Long clienteId,
    String clienteNombre,
    String clienteEmail,
    BigDecimal total,
    List<LineaEvento> detalles)
    implements EventoPedidos360 {

  /** Nombre logico que enlaza el evento con su routing key configurada. */
  public static final String NOMBRE = "pedido-confirmado";

  /**
   * Linea del pedido tal como viaja en el evento.
   *
   * @param sku codigo del producto
   * @param descripcion nombre del producto
   * @param cantidad unidades solicitadas
   * @param precioUnitario precio por unidad al momento del pedido
   * @param subtotal cantidad por precio unitario
   */
  public record LineaEvento(
      String sku,
      String descripcion,
      int cantidad,
      BigDecimal precioUnitario,
      BigDecimal subtotal) {}

  /**
   * Crea el evento con identificador y marca de tiempo nuevos.
   *
   * @param pedidoNumero correlativo del pedido
   * @param pedidoId identificador interno del pedido
   * @param clienteId cliente dueno del pedido
   * @param clienteNombre nombre del cliente
   * @param clienteEmail correo de contacto
   * @param total monto total
   * @param detalles lineas del pedido
   * @return evento listo para publicar
   */
  public static PedidoConfirmadoEvento de(
      String pedidoNumero,
      Long pedidoId,
      Long clienteId,
      String clienteNombre,
      String clienteEmail,
      BigDecimal total,
      List<LineaEvento> detalles) {
    return new PedidoConfirmadoEvento(
        UUID.randomUUID().toString(),
        Instant.now(),
        pedidoNumero,
        pedidoId,
        clienteId,
        clienteNombre,
        clienteEmail,
        total,
        List.copyOf(detalles));
  }

  @Override
  public String nombreLogico() {
    return NOMBRE;
  }
}
