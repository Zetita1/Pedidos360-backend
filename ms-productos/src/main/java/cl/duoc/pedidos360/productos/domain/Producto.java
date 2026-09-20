package cl.duoc.pedidos360.productos.domain;

import cl.duoc.pedidos360.web.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Producto del catalogo, con control de stock optimista para evitar sobreventa. */
@Entity
@Table(name = "producto")
public class Producto {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Size(max = 40)
  @Column(nullable = false, unique = true, length = 40)
  private String sku;

  @NotBlank
  @Size(max = 150)
  @Column(nullable = false, length = 150)
  private String nombre;

  @Size(max = 500)
  @Column(length = 500)
  private String descripcion;

  @NotNull
  @DecimalMin(value = "0.0", inclusive = false)
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal precio;

  @NotNull
  @PositiveOrZero
  @Column(nullable = false)
  private Integer stock;

  @NotBlank
  @Size(max = 60)
  @Column(nullable = false, length = 60)
  private String categoria;

  @Column(nullable = false)
  private boolean activo = true;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Producto() {
    // requerido por JPA
  }

  public Producto(
      String sku,
      String nombre,
      String descripcion,
      BigDecimal precio,
      Integer stock,
      String categoria) {
    this.sku = sku;
    this.nombre = nombre;
    this.descripcion = descripcion;
    this.precio = precio;
    this.stock = stock;
    this.categoria = categoria;
    this.activo = true;
  }

  @PrePersist
  void alCrear() {
    Instant ahora = Instant.now();
    this.creadoEn = ahora;
    this.actualizadoEn = ahora;
  }

  @PreUpdate
  void alActualizar() {
    this.actualizadoEn = Instant.now();
  }

  /**
   * Descuenta unidades del stock validando disponibilidad.
   *
   * @param cantidad unidades a descontar
   * @throws ReglaNegocioException si el producto esta inactivo o no hay stock suficiente
   */
  public void descontarStock(int cantidad) {
    if (cantidad <= 0) {
      throw new IllegalArgumentException("La cantidad a descontar debe ser mayor a cero");
    }
    if (!activo) {
      throw new ReglaNegocioException("El producto " + sku + " no esta disponible para la venta");
    }
    if (stock < cantidad) {
      throw new ReglaNegocioException(
          "Stock insuficiente para " + sku + ": disponible " + stock + ", solicitado " + cantidad);
    }
    this.stock -= cantidad;
  }

  /**
   * Repone unidades al stock (devoluciones o anulacion de pedidos).
   *
   * @param cantidad unidades a reponer
   */
  public void reponerStock(int cantidad) {
    if (cantidad <= 0) {
      throw new IllegalArgumentException("La cantidad a reponer debe ser mayor a cero");
    }
    this.stock += cantidad;
  }

  public void actualizarDatos(
      String nombre, String descripcion, BigDecimal precio, Integer stock, String categoria) {
    this.nombre = nombre;
    this.descripcion = descripcion;
    this.precio = precio;
    this.stock = stock;
    this.categoria = categoria;
  }

  public void desactivar() {
    this.activo = false;
  }

  public void activar() {
    this.activo = true;
  }

  public Long getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public String getNombre() {
    return nombre;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public BigDecimal getPrecio() {
    return precio;
  }

  public Integer getStock() {
    return stock;
  }

  public String getCategoria() {
    return categoria;
  }

  public boolean isActivo() {
    return activo;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  public Long getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Producto otro)) {
      return false;
    }
    return id != null && Objects.equals(id, otro.id);
  }

  @Override
  public int hashCode() {
    return Producto.class.hashCode();
  }
}
