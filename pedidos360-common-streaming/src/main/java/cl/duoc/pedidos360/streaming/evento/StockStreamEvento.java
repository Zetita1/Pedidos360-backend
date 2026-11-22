package cl.duoc.pedidos360.streaming.evento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro del estado de stock de un producto.
 *
 * <p>Va en un topico compactado, asi que cada registro reemplaza al anterior de la misma
 * clave: el topico siempre refleja el stock vigente de todo el catalogo y se puede leer
 * desde el principio para reconstruirlo.
 *
 * <p>El evento lleva el valor anterior y el actual, no solo la diferencia. Con el valor
 * absoluto, un consumidor que se reincorpora tras una caida obtiene el estado correcto con
 * un solo registro; si solo viajara el delta tendria que haber leido todos los anteriores,
 * y un registro perdido o repetido dejaria el stock mal para siempre.
 *
 * @param eventoId identificador unico del registro
 * @param productoId identificador del producto, clave de particionado
 * @param sku codigo del producto
 * @param nombre nombre del producto
 * @param categoria categoria del catalogo, para agrupar sin consultar el catalogo
 * @param precio precio vigente, para valorizar el inventario
 * @param stockAnterior stock antes del movimiento
 * @param stockActual stock despues del movimiento
 * @param motivo origen del movimiento
 * @param ocurridoEn momento del movimiento
 */
public record StockStreamEvento(
    @NotBlank String eventoId,
    @NotNull Long productoId,
    @NotBlank String sku,
    String nombre,
    String categoria,
    @PositiveOrZero BigDecimal precio,
    @PositiveOrZero Integer stockAnterior,
    @NotNull @PositiveOrZero Integer stockActual,
    String motivo,
    @NotNull Instant ocurridoEn)
    implements EventoStream {

  @Override
  public String clave() {
    return String.valueOf(productoId);
  }

  /**
   * Movimiento neto del stock, positivo cuando se repuso y negativo cuando se descont&oacute;.
   *
   * @return diferencia entre el stock actual y el anterior
   */
  public int delta() {
    return stockAnterior == null ? stockActual : stockActual - stockAnterior;
  }

  /**
   * Construye el registro de un movimiento de stock.
   *
   * @param productoId identificador del producto
   * @param sku codigo del producto
   * @param nombre nombre del producto
   * @param categoria categoria del catalogo
   * @param precio precio vigente
   * @param stockAnterior stock antes del movimiento
   * @param stockActual stock despues del movimiento
   * @param motivo origen del movimiento
   * @return registro listo para publicar
   */
  public static StockStreamEvento de(
      Long productoId,
      String sku,
      String nombre,
      String categoria,
      BigDecimal precio,
      Integer stockAnterior,
      Integer stockActual,
      String motivo) {
    return new StockStreamEvento(
        UUID.randomUUID().toString(),
        productoId,
        sku,
        nombre,
        categoria,
        precio,
        stockAnterior,
        stockActual,
        motivo,
        Instant.now());
  }
}
