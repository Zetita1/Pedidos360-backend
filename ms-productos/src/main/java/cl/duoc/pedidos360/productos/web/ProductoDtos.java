package cl.duoc.pedidos360.productos.web;

import cl.duoc.pedidos360.productos.domain.Producto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Contratos de entrada y salida del API de productos. */
public final class ProductoDtos {

  private ProductoDtos() {}

  /** Representacion publica de un producto. */
  public record ProductoResponse(
      Long id,
      String sku,
      String nombre,
      String descripcion,
      BigDecimal precio,
      Integer stock,
      String categoria,
      boolean activo,
      Instant actualizadoEn) {

    public static ProductoResponse de(Producto producto) {
      return new ProductoResponse(
          producto.getId(),
          producto.getSku(),
          producto.getNombre(),
          producto.getDescripcion(),
          producto.getPrecio(),
          producto.getStock(),
          producto.getCategoria(),
          producto.isActivo(),
          producto.getActualizadoEn());
    }
  }

  /** Datos para crear un producto nuevo. */
  public record CrearProductoRequest(
      @NotBlank @Size(max = 40) String sku,
      @NotBlank @Size(max = 150) String nombre,
      @Size(max = 500) String descripcion,
      @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal precio,
      @NotNull @PositiveOrZero Integer stock,
      @NotBlank @Size(max = 60) String categoria) {}

  /** Datos editables de un producto existente. */
  public record ActualizarProductoRequest(
      @NotBlank @Size(max = 150) String nombre,
      @Size(max = 500) String descripcion,
      @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal precio,
      @NotNull @PositiveOrZero Integer stock,
      @NotBlank @Size(max = 60) String categoria) {}

  /** Movimiento de stock solicitado por el microservicio de pedidos. */
  public record MovimientoStockRequest(@NotNull @Positive Integer cantidad, String motivo) {}
}
