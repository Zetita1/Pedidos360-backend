package cl.duoc.pedidos360.analitica.web;

import cl.duoc.pedidos360.analitica.domain.AlertaStock;
import cl.duoc.pedidos360.analitica.domain.EstadoStock;
import cl.duoc.pedidos360.analitica.domain.EventoProcesado;
import cl.duoc.pedidos360.analitica.domain.MetricaDiaria;
import cl.duoc.pedidos360.analitica.domain.VentaPorCliente;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Contratos de salida del API de analitica. */
public final class AnaliticaDtos {

  private AnaliticaDtos() {}

  /**
   * Cifras del dia en curso mas el estado del inventario.
   *
   * @param fecha dia al que corresponden las cifras
   * @param pedidosCreados pedidos registrados hoy
   * @param pedidosConfirmados pedidos confirmados hoy
   * @param pedidosAnulados pedidos anulados hoy
   * @param montoConfirmado venta confirmada del dia
   * @param ticketPromedio venta promedio por pedido confirmado
   * @param lineasVendidas lineas de detalle confirmadas
   * @param productosSeguidos productos con estado conocido en la proyeccion
   * @param productosCriticos productos en o bajo el umbral
   * @param umbralCritico umbral vigente
   * @param inventarioValorizado valor del inventario proyectado
   * @param eventosProcesados registros consumidos desde los topicos
   * @param actualizadoEn ultima vez que un evento modifico las cifras
   */
  public record ResumenResponse(
      LocalDate fecha,
      int pedidosCreados,
      int pedidosConfirmados,
      int pedidosAnulados,
      BigDecimal montoConfirmado,
      BigDecimal ticketPromedio,
      int lineasVendidas,
      long productosSeguidos,
      long productosCriticos,
      int umbralCritico,
      BigDecimal inventarioValorizado,
      long eventosProcesados,
      Instant actualizadoEn) {}

  /**
   * Metricas de un dia.
   *
   * @param fecha dia al que corresponden
   * @param pedidosCreados pedidos registrados
   * @param pedidosConfirmados pedidos confirmados
   * @param pedidosAnulados pedidos anulados
   * @param montoCreado monto registrado
   * @param montoConfirmado monto confirmado
   * @param montoAnulado monto anulado
   * @param ticketPromedio venta promedio por pedido confirmado
   * @param lineasVendidas lineas de detalle confirmadas
   * @param actualizadoEn ultima actualizacion
   */
  public record MetricaDiariaResponse(
      LocalDate fecha,
      int pedidosCreados,
      int pedidosConfirmados,
      int pedidosAnulados,
      BigDecimal montoCreado,
      BigDecimal montoConfirmado,
      BigDecimal montoAnulado,
      BigDecimal ticketPromedio,
      int lineasVendidas,
      Instant actualizadoEn) {

    /**
     * @param metrica proyeccion persistida
     * @return representacion publica
     */
    public static MetricaDiariaResponse de(MetricaDiaria metrica) {
      return new MetricaDiariaResponse(
          metrica.getFecha(),
          metrica.getPedidosCreados(),
          metrica.getPedidosConfirmados(),
          metrica.getPedidosAnulados(),
          metrica.getMontoCreado(),
          metrica.getMontoConfirmado(),
          metrica.getMontoAnulado(),
          metrica.ticketPromedio(),
          metrica.getLineasVendidas(),
          metrica.getActualizadoEn());
    }
  }

  /**
   * Acumulado de un cliente.
   *
   * @param clienteId identificador del cliente
   * @param clienteNombre razon social
   * @param pedidosConfirmados pedidos confirmados
   * @param montoTotal monto acumulado
   * @param ultimaCompraEn ultima compra registrada
   */
  public record VentaClienteResponse(
      Long clienteId,
      String clienteNombre,
      int pedidosConfirmados,
      BigDecimal montoTotal,
      Instant ultimaCompraEn) {

    /**
     * @param venta acumulado persistido
     * @return representacion publica
     */
    public static VentaClienteResponse de(VentaPorCliente venta) {
      return new VentaClienteResponse(
          venta.getClienteId(),
          venta.getClienteNombre(),
          venta.getPedidosConfirmados(),
          venta.getMontoTotal(),
          venta.getUltimaCompraEn());
    }
  }

  /**
   * Stock vigente de un producto.
   *
   * @param productoId identificador del producto
   * @param sku codigo del producto
   * @param nombre nombre del producto
   * @param categoria categoria del catalogo
   * @param precio precio vigente
   * @param stock unidades disponibles
   * @param stockAnterior unidades antes del ultimo movimiento
   * @param motivo origen del ultimo movimiento
   * @param valorizado valor del inventario de este producto
   * @param critico si esta en o bajo el umbral
   * @param actualizadoEn momento del ultimo movimiento
   */
  public record EstadoStockResponse(
      Long productoId,
      String sku,
      String nombre,
      String categoria,
      BigDecimal precio,
      Integer stock,
      Integer stockAnterior,
      String motivo,
      BigDecimal valorizado,
      boolean critico,
      Instant actualizadoEn) {

    /**
     * @param estado proyeccion persistida
     * @param umbral umbral vigente
     * @return representacion publica
     */
    public static EstadoStockResponse de(EstadoStock estado, int umbral) {
      return new EstadoStockResponse(
          estado.getProductoId(),
          estado.getSku(),
          estado.getNombre(),
          estado.getCategoria(),
          estado.getPrecio(),
          estado.getStock(),
          estado.getStockAnterior(),
          estado.getMotivo(),
          estado.valorizado(),
          estado.getStock() != null && estado.getStock() <= umbral,
          estado.getActualizadoEn());
    }
  }

  /**
   * Aviso de stock critico.
   *
   * @param id identificador del aviso
   * @param productoId producto afectado
   * @param sku codigo del producto
   * @param nombre nombre del producto
   * @param stock stock que lo provoco
   * @param umbral umbral vigente al generarlo
   * @param mensaje texto del aviso
   * @param eventoId registro del topico que lo origino
   * @param generadaEn momento en que se genero
   */
  public record AlertaResponse(
      Long id,
      Long productoId,
      String sku,
      String nombre,
      Integer stock,
      Integer umbral,
      String mensaje,
      String eventoId,
      Instant generadaEn) {

    /**
     * @param alerta aviso persistido
     * @return representacion publica
     */
    public static AlertaResponse de(AlertaStock alerta) {
      return new AlertaResponse(
          alerta.getId(),
          alerta.getProductoId(),
          alerta.getSku(),
          alerta.getNombre(),
          alerta.getStock(),
          alerta.getUmbral(),
          alerta.getMensaje(),
          alerta.getEventoId(),
          alerta.getGeneradaEn());
    }
  }

  /**
   * Registro ya consumido, con sus coordenadas en el topico.
   *
   * @param eventoId identificador del evento
   * @param topico topico de origen
   * @param particion particion de origen
   * @param offset posicion en la particion
   * @param clave clave de particionado
   * @param tipo hecho que traia
   * @param procesadoEn momento en que se consumio
   */
  public record EventoResponse(
      String eventoId,
      String topico,
      int particion,
      long offset,
      String clave,
      String tipo,
      Instant procesadoEn) {

    /**
     * @param evento registro persistido
     * @return representacion publica
     */
    public static EventoResponse de(EventoProcesado evento) {
      return new EventoResponse(
          evento.getEventoId(),
          evento.getTopico(),
          evento.getParticion(),
          evento.getOffset(),
          evento.getClave(),
          evento.getTipo(),
          evento.getProcesadoEn());
    }
  }
}
