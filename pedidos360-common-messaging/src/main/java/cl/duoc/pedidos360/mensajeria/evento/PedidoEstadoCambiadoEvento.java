package cl.duoc.pedidos360.mensajeria.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Un pedido cambio de estado (despachado, entregado, anulado, etc.). Dispara la notificacion
 * de seguimiento al cliente.
 *
 * @param eventoId identificador unico del evento
 * @param ocurridoEn momento del hecho
 * @param pedidoNumero correlativo del pedido
 * @param pedidoId identificador interno del pedido
 * @param clienteId cliente dueno del pedido
 * @param clienteEmail correo de contacto
 * @param estadoAnterior estado del que viene el pedido
 * @param estadoNuevo estado al que paso
 * @param motivo justificacion del cambio, si la hubo
 */
public record PedidoEstadoCambiadoEvento(
    String eventoId,
    Instant ocurridoEn,
    String pedidoNumero,
    Long pedidoId,
    Long clienteId,
    String clienteEmail,
    String estadoAnterior,
    String estadoNuevo,
    String motivo)
    implements EventoPedidos360 {

  /** Nombre logico que enlaza el evento con su routing key configurada. */
  public static final String NOMBRE = "pedido-estado-cambiado";

  /**
   * Crea el evento con identificador y marca de tiempo nuevos.
   *
   * @param pedidoNumero correlativo del pedido
   * @param pedidoId identificador interno del pedido
   * @param clienteId cliente dueno del pedido
   * @param clienteEmail correo de contacto
   * @param estadoAnterior estado previo
   * @param estadoNuevo estado destino
   * @param motivo justificacion del cambio
   * @return evento listo para publicar
   */
  public static PedidoEstadoCambiadoEvento de(
      String pedidoNumero,
      Long pedidoId,
      Long clienteId,
      String clienteEmail,
      String estadoAnterior,
      String estadoNuevo,
      String motivo) {
    return new PedidoEstadoCambiadoEvento(
        UUID.randomUUID().toString(),
        Instant.now(),
        pedidoNumero,
        pedidoId,
        clienteId,
        clienteEmail,
        estadoAnterior,
        estadoNuevo,
        motivo);
  }

  @Override
  public String nombreLogico() {
    return NOMBRE;
  }
}
