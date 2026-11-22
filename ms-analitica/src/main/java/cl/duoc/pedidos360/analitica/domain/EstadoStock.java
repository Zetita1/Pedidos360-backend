package cl.duoc.pedidos360.analitica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Stock vigente de un producto, proyectado desde el topico compactado.
 *
 * <p>Hay una fila por producto y el consumidor la sobrescribe con cada registro, igual que
 * Kafka sobrescribe la clave en el topico. La tabla y el topico dicen lo mismo, y por eso la
 * tabla se puede perder sin consecuencias: leyendo el topico desde el principio se reconstruye
 * completa.
 */
@Entity
@Table(name = "estado_stock")
public class EstadoStock {

  /** El id del producto es la clave de particionado del topico y tambien la de esta tabla. */
  @Id
  @Column(name = "producto_id")
  private Long productoId;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(length = 180)
  private String nombre;

  @Column(length = 80)
  private String categoria;

  @Column(precision = 12, scale = 2)
  private BigDecimal precio;

  @Column(nullable = false)
  private Integer stock;

  @Column(name = "stock_anterior")
  private Integer stockAnterior;

  @Column(length = 180)
  private String motivo;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version private Long version;

  protected EstadoStock() {}

  /**
   * @param productoId identificador del producto
   * @param sku codigo del producto
   */
  public EstadoStock(Long productoId, String sku) {
    this.productoId = productoId;
    this.sku = sku;
    this.stock = 0;
    this.actualizadoEn = Instant.now();
  }

  /**
   * Aplica el ultimo registro conocido del producto.
   *
   * @param sku codigo del producto
   * @param nombre nombre del producto
   * @param categoria categoria del catalogo
   * @param precio precio vigente
   * @param stockAnterior stock previo al movimiento
   * @param stock stock resultante
   * @param motivo origen del movimiento
   * @param ocurridoEn momento del movimiento
   */
  public void aplicar(
      String sku,
      String nombre,
      String categoria,
      BigDecimal precio,
      Integer stockAnterior,
      Integer stock,
      String motivo,
      Instant ocurridoEn) {
    this.sku = sku;
    this.nombre = nombre;
    this.categoria = categoria;
    this.precio = precio;
    this.stockAnterior = stockAnterior;
    this.stock = stock;
    this.motivo = motivo;
    this.actualizadoEn = ocurridoEn == null ? Instant.now() : ocurridoEn;
  }

  /**
   * Valor del inventario de este producto.
   *
   * @return precio por unidades disponibles, cero si falta el precio
   */
  public BigDecimal valorizado() {
    if (precio == null || stock == null) {
      return BigDecimal.ZERO;
    }
    return precio.multiply(BigDecimal.valueOf(stock));
  }

  public Long getProductoId() {
    return productoId;
  }

  public String getSku() {
    return sku;
  }

  public String getNombre() {
    return nombre;
  }

  public String getCategoria() {
    return categoria;
  }

  public BigDecimal getPrecio() {
    return precio;
  }

  public Integer getStock() {
    return stock;
  }

  public Integer getStockAnterior() {
    return stockAnterior;
  }

  public String getMotivo() {
    return motivo;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof EstadoStock otro
        && productoId != null
        && productoId.equals(otro.productoId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(productoId);
  }
}
