package cl.duoc.pedidos360.bff.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Modelos propios del BFF, pensados para lo que dibuja el frontend Angular. */
public final class BffModels {

  private BffModels() {}

  /** Permisos derivados de los roles del token, para que el SPA muestre u oculte acciones. */
  public record Permisos(
      boolean gestionarCatalogo,
      boolean gestionarClientes,
      boolean crearPedidos,
      boolean anularPedidos,
      boolean verTablero) {}

  /** Identidad del usuario tal como la ve el frontend, resuelta desde los claims del token. */
  public record PerfilUsuario(
      String id,
      String nombre,
      String email,
      String tenantId,
      List<String> roles,
      List<String> scopes,
      Permisos permisos) {}

  /** Producto con stock bajo el umbral de alerta. */
  public record ProductoCritico(Long id, String sku, String nombre, Integer stock, BigDecimal precio) {}

  /** Pedido resumido para la tabla del tablero. */
  public record PedidoResumen(
      Long id, String numero, String cliente, String estado, BigDecimal total, String creadoEn) {}

  /** Datos agregados que alimentan el tablero de inicio en una sola llamada. */
  public record Tablero(
      long totalPedidos,
      Map<String, Long> pedidosPorEstado,
      BigDecimal montoEnCurso,
      long productosActivos,
      List<ProductoCritico> stockCritico,
      List<PedidoResumen> ultimosPedidos) {}
}
