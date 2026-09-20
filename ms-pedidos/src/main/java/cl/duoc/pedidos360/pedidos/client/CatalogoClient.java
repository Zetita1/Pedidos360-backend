package cl.duoc.pedidos360.pedidos.client;

import java.math.BigDecimal;

/** Puerto hacia el microservicio de productos. */
public interface CatalogoClient {

  /**
   * Obtiene el producto desde el catalogo.
   *
   * @param productoId identificador del producto
   * @return datos vigentes del producto
   */
  ProductoInfo obtener(Long productoId);

  /**
   * Descuenta stock al confirmar un pedido.
   *
   * @param productoId identificador del producto
   * @param cantidad unidades a descontar
   * @param motivo trazabilidad del movimiento
   */
  void descontarStock(Long productoId, int cantidad, String motivo);

  /**
   * Repone stock al anular un pedido.
   *
   * @param productoId identificador del producto
   * @param cantidad unidades a reponer
   * @param motivo trazabilidad del movimiento
   */
  void reponerStock(Long productoId, int cantidad, String motivo);

  /** Vista reducida del producto que necesita el pedido. */
  record ProductoInfo(
      Long id, String sku, String nombre, BigDecimal precio, Integer stock, boolean activo) {}
}
