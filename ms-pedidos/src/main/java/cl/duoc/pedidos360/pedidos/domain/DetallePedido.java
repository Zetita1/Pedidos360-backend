package cl.duoc.pedidos360.pedidos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Linea de un pedido. Guarda una foto del producto al momento de la venta (sku, nombre y
 * precio) para que el historico no cambie si despues se edita el catalogo.
 */
@Entity
@Table(name = "detalle_pedido")
public class DetallePedido {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false)
  private Pedido pedido;

  @NotNull
  @Column(name = "producto_id", nullable = false)
  private Long productoId;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(name = "nombre_producto", nullable = false, length = 150)
  private String nombreProducto;

  @NotNull
  @Positive
  @Column(nullable = false)
  private Integer cantidad;

  @NotNull
  @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
  private BigDecimal precioUnitario;

  protected DetallePedido() {
    // requerido por JPA
  }

  public DetallePedido(
      Long productoId, String sku, String nombreProducto, Integer cantidad, BigDecimal precioUnitario) {
    this.productoId = productoId;
    this.sku = sku;
    this.nombreProducto = nombreProducto;
    this.cantidad = cantidad;
    this.precioUnitario = precioUnitario;
  }

  void asociarA(Pedido pedido) {
    this.pedido = pedido;
  }

  /**
   * Valor de la linea.
   *
   * @return precio unitario por cantidad
   */
  public BigDecimal subtotal() {
    return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
  }

  public Long getId() {
    return id;
  }

  public Pedido getPedido() {
    return pedido;
  }

  public Long getProductoId() {
    return productoId;
  }

  public String getSku() {
    return sku;
  }

  public String getNombreProducto() {
    return nombreProducto;
  }

  public Integer getCantidad() {
    return cantidad;
  }

  public BigDecimal getPrecioUnitario() {
    return precioUnitario;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DetallePedido otro)) {
      return false;
    }
    return id != null && Objects.equals(id, otro.id);
  }

  @Override
  public int hashCode() {
    return DetallePedido.class.hashCode();
  }
}
