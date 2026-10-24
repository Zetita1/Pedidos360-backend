package cl.duoc.pedidos360.mensajeria.evento;

import cl.duoc.pedidos360.mensajeria.config.TipoExchange;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un pedido fue anulado. A diferencia de los demas eventos, este viaja por el exchange
 * <b>direct</b>: no es un hecho que se difunde, es una orden con un destinatario unico,
 * registrar la operacion en auditoria.
 *
 * @param eventoId identificador unico del evento
 * @param ocurridoEn momento del hecho
 * @param pedidoNumero correlativo del pedido
 * @param pedidoId identificador interno del pedido
 * @param clienteId cliente dueno del pedido
 * @param usuarioOid identidad de quien ejecuto la anulacion
 * @param usuarioNombre nombre legible de quien la ejecuto
 * @param estadoAnterior estado que tenia el pedido
 * @param total monto que se dio de baja
 * @param motivo justificacion de la anulacion
 */
public record PedidoAnuladoEvento(
    String eventoId,
    Instant ocurridoEn,
    String pedidoNumero,
    Long pedidoId,
    Long clienteId,
    String usuarioOid,
    String usuarioNombre,
    String estadoAnterior,
    BigDecimal total,
    String motivo)
    implements EventoPedidos360 {

  /** Nombre logico que enlaza el evento con su routing key configurada. */
  public static final String NOMBRE = "pedido-anulado";

  /**
   * Crea el evento con identificador y marca de tiempo nuevos.
   *
   * @param pedidoNumero correlativo del pedido
   * @param pedidoId identificador interno del pedido
   * @param clienteId cliente dueno del pedido
   * @param usuarioOid identidad de quien anula
   * @param usuarioNombre nombre de quien anula
   * @param estadoAnterior estado previo del pedido
   * @param total monto dado de baja
   * @param motivo justificacion
   * @return evento listo para publicar
   */
  public static PedidoAnuladoEvento de(
      String pedidoNumero,
      Long pedidoId,
      Long clienteId,
      String usuarioOid,
      String usuarioNombre,
      String estadoAnterior,
      BigDecimal total,
      String motivo) {
    return new PedidoAnuladoEvento(
        UUID.randomUUID().toString(),
        Instant.now(),
        pedidoNumero,
        pedidoId,
        clienteId,
        usuarioOid,
        usuarioNombre,
        estadoAnterior,
        total,
        motivo);
  }

  @Override
  public String nombreLogico() {
    return NOMBRE;
  }

  @Override
  public TipoExchange tipoExchange() {
    return TipoExchange.COMANDOS;
  }
}
