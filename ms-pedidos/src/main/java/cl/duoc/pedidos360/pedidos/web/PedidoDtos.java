package cl.duoc.pedidos360.pedidos.web;

import cl.duoc.pedidos360.pedidos.domain.DetallePedido;
import cl.duoc.pedidos360.pedidos.domain.EstadoPedido;
import cl.duoc.pedidos360.pedidos.domain.Pedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Contratos de entrada y salida del API de pedidos. */
public final class PedidoDtos {

  private PedidoDtos() {}

  /** Linea del pedido tal como la envia el frontend. */
  public record LineaRequest(@NotNull Long productoId, @NotNull @Positive Integer cantidad) {}

  /** Datos para registrar un pedido nuevo. */
  public record CrearPedidoRequest(
      @NotNull Long clienteId,
      @Size(max = 250) String direccionDespacho,
      @Size(max = 300) String observacion,
      @NotEmpty @Valid List<LineaRequest> lineas) {}

  /** Cambio de estado solicitado sobre un pedido. */
  public record CambioEstadoRequest(@NotNull EstadoPedido estado, @Size(max = 300) String motivo) {}

  /** Linea del pedido en la respuesta. */
  public record LineaResponse(
      Long id,
      Long productoId,
      String sku,
      String nombreProducto,
      Integer cantidad,
      BigDecimal precioUnitario,
      BigDecimal subtotal) {

    public static LineaResponse de(DetallePedido detalle) {
      return new LineaResponse(
          detalle.getId(),
          detalle.getProductoId(),
          detalle.getSku(),
          detalle.getNombreProducto(),
          detalle.getCantidad(),
          detalle.getPrecioUnitario(),
          detalle.subtotal());
    }
  }

  /** Representacion completa de un pedido. */
  public record PedidoResponse(
      Long id,
      String numero,
      Long clienteId,
      String clienteRut,
      String clienteRazonSocial,
      EstadoPedido estado,
      String direccionDespacho,
      String observacion,
      BigDecimal neto,
      BigDecimal iva,
      BigDecimal total,
      String registradoPor,
      List<LineaResponse> lineas,
      Instant creadoEn,
      Instant actualizadoEn) {

    public static PedidoResponse de(Pedido pedido) {
      return new PedidoResponse(
          pedido.getId(),
          pedido.getNumero(),
          pedido.getClienteId(),
          pedido.getClienteRut(),
          pedido.getClienteRazonSocial(),
          pedido.getEstado(),
          pedido.getDireccionDespacho(),
          pedido.getObservacion(),
          pedido.getNeto(),
          pedido.getIva(),
          pedido.getTotal(),
          pedido.getUsuarioNombre(),
          pedido.getDetalles().stream().map(LineaResponse::de).toList(),
          pedido.getCreadoEn(),
          pedido.getActualizadoEn());
    }
  }

  /** Resumen usado por el tablero del frontend. */
  public record ResumenEstado(EstadoPedido estado, long cantidad) {}
}
