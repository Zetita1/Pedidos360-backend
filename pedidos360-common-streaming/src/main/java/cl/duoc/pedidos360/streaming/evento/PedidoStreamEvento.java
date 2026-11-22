package cl.duoc.pedidos360.streaming.evento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro del flujo de hechos de pedidos.
 *
 * <p>Cada campo esta porque un consumidor lo necesita, y el criterio es que el evento se
 * pueda procesar sin volver a consultar a nadie: si la analitica tuviera que pedirle el
 * nombre del cliente a ms-clientes por cada registro, el streaming dejaria de ser asincrono
 * y volveria a acoplar los microservicios.
 *
 * <ul>
 *   <li>{@code eventoId} descarta duplicados.
 *   <li>{@code tipo} dice que hecho ocurrio; un mismo topico lleva los cuatro.
 *   <li>{@code pedidoNumero} es la clave de particionado: ordena los hechos de un pedido.
 *   <li>{@code clienteId} y {@code clienteNombre} permiten agrupar ventas por cliente sin
 *       consultar el catalogo de clientes.
 *   <li>{@code total} y {@code cantidadLineas} alimentan los montos y los promedios.
 *   <li>{@code estadoAnterior} y {@code estadoNuevo} permiten medir el embudo de estados.
 *   <li>{@code motivo} y {@code usuarioOid} dan trazabilidad de quien hizo el cambio y por que.
 *   <li>{@code ocurridoEn} ubica el hecho en el tiempo.
 * </ul>
 *
 * @param eventoId identificador unico del registro
 * @param tipo hecho de negocio que lo origino
 * @param pedidoNumero correlativo del pedido, clave de particionado
 * @param pedidoId identificador interno del pedido
 * @param clienteId identificador del cliente
 * @param clienteNombre razon social del cliente
 * @param total monto total del pedido
 * @param cantidadLineas numero de lineas de detalle
 * @param estadoAnterior estado previo, nulo cuando el pedido se acaba de crear
 * @param estadoNuevo estado resultante del hecho
 * @param motivo justificacion del cambio, cuando la hay
 * @param usuarioOid identidad de quien provoco el hecho
 * @param ocurridoEn momento del hecho
 */
public record PedidoStreamEvento(
    @NotBlank String eventoId,
    @NotNull TipoHechoPedido tipo,
    @NotBlank String pedidoNumero,
    Long pedidoId,
    @NotNull Long clienteId,
    String clienteNombre,
    @NotNull @PositiveOrZero BigDecimal total,
    @PositiveOrZero int cantidadLineas,
    String estadoAnterior,
    @NotBlank String estadoNuevo,
    String motivo,
    String usuarioOid,
    @NotNull Instant ocurridoEn)
    implements EventoStream {

  @Override
  public String clave() {
    return pedidoNumero;
  }

  /**
   * Construye el registro de un pedido recien creado.
   *
   * @param pedidoNumero correlativo del pedido
   * @param pedidoId identificador interno
   * @param clienteId identificador del cliente
   * @param clienteNombre razon social del cliente
   * @param total monto total
   * @param cantidadLineas numero de lineas
   * @param usuarioOid identidad del usuario
   * @return registro listo para publicar
   */
  public static PedidoStreamEvento creado(
      String pedidoNumero,
      Long pedidoId,
      Long clienteId,
      String clienteNombre,
      BigDecimal total,
      int cantidadLineas,
      String usuarioOid) {
    return new PedidoStreamEvento(
        UUID.randomUUID().toString(),
        TipoHechoPedido.CREADO,
        pedidoNumero,
        pedidoId,
        clienteId,
        clienteNombre,
        total,
        cantidadLineas,
        null,
        "BORRADOR",
        null,
        usuarioOid,
        Instant.now());
  }

  /**
   * Construye el registro de un pedido confirmado.
   *
   * @param pedidoNumero correlativo del pedido
   * @param pedidoId identificador interno
   * @param clienteId identificador del cliente
   * @param clienteNombre razon social del cliente
   * @param total monto total
   * @param cantidadLineas numero de lineas
   * @param usuarioOid identidad del usuario
   * @return registro listo para publicar
   */
  public static PedidoStreamEvento confirmado(
      String pedidoNumero,
      Long pedidoId,
      Long clienteId,
      String clienteNombre,
      BigDecimal total,
      int cantidadLineas,
      String usuarioOid) {
    return new PedidoStreamEvento(
        UUID.randomUUID().toString(),
        TipoHechoPedido.CONFIRMADO,
        pedidoNumero,
        pedidoId,
        clienteId,
        clienteNombre,
        total,
        cantidadLineas,
        "BORRADOR",
        "CONFIRMADO",
        null,
        usuarioOid,
        Instant.now());
  }

  /**
   * Construye el registro de un cambio de estado.
   *
   * @param pedidoNumero correlativo del pedido
   * @param pedidoId identificador interno
   * @param clienteId identificador del cliente
   * @param clienteNombre razon social del cliente
   * @param total monto total
   * @param cantidadLineas numero de lineas
   * @param estadoAnterior estado previo
   * @param estadoNuevo estado resultante
   * @param motivo justificacion del cambio
   * @param usuarioOid identidad del usuario
   * @return registro listo para publicar
   */
  public static PedidoStreamEvento estadoCambiado(
      String pedidoNumero,
      Long pedidoId,
      Long clienteId,
      String clienteNombre,
      BigDecimal total,
      int cantidadLineas,
      String estadoAnterior,
      String estadoNuevo,
      String motivo,
      String usuarioOid) {
    return new PedidoStreamEvento(
        UUID.randomUUID().toString(),
        "ANULADO".equals(estadoNuevo) ? TipoHechoPedido.ANULADO : TipoHechoPedido.ESTADO_CAMBIADO,
        pedidoNumero,
        pedidoId,
        clienteId,
        clienteNombre,
        total,
        cantidadLineas,
        estadoAnterior,
        estadoNuevo,
        motivo,
        usuarioOid,
        Instant.now());
  }
}
