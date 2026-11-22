package cl.duoc.pedidos360.analitica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Metricas de un dia, construidas sumando los hechos que llegan por el topico de pedidos.
 *
 * <p>Es una proyeccion: no la escribe nadie a mano, se recalcula sola a medida que el flujo
 * avanza. Si hubiera que reconstruirla, basta borrar la tabla y volver a leer el topico desde
 * el principio.
 */
@Entity
@Table(name = "metrica_diaria")
public class MetricaDiaria {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Dia al que corresponden las cifras, tomado del momento del hecho. */
  @Column(nullable = false, unique = true)
  private LocalDate fecha;

  @Column(name = "pedidos_creados", nullable = false)
  private int pedidosCreados;

  @Column(name = "pedidos_confirmados", nullable = false)
  private int pedidosConfirmados;

  @Column(name = "pedidos_anulados", nullable = false)
  private int pedidosAnulados;

  /** Monto de los pedidos registrados en el dia, confirmados o no. */
  @Column(name = "monto_creado", nullable = false, precision = 15, scale = 2)
  private BigDecimal montoCreado = BigDecimal.ZERO;

  /** Monto efectivamente confirmado: es la venta del dia. */
  @Column(name = "monto_confirmado", nullable = false, precision = 15, scale = 2)
  private BigDecimal montoConfirmado = BigDecimal.ZERO;

  /** Monto perdido por anulaciones. */
  @Column(name = "monto_anulado", nullable = false, precision = 15, scale = 2)
  private BigDecimal montoAnulado = BigDecimal.ZERO;

  @Column(name = "lineas_vendidas", nullable = false)
  private int lineasVendidas;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn = Instant.now();

  @Version private Long version;

  protected MetricaDiaria() {}

  /**
   * @param fecha dia al que corresponden las cifras
   */
  public MetricaDiaria(LocalDate fecha) {
    this.fecha = fecha;
  }

  /**
   * Suma un pedido recien registrado.
   *
   * @param monto total del pedido
   */
  public void registrarCreado(BigDecimal monto) {
    pedidosCreados++;
    montoCreado = montoCreado.add(valor(monto));
    marcar();
  }

  /**
   * Suma un pedido confirmado.
   *
   * @param monto total del pedido
   * @param lineas lineas de detalle del pedido
   */
  public void registrarConfirmado(BigDecimal monto, int lineas) {
    pedidosConfirmados++;
    montoConfirmado = montoConfirmado.add(valor(monto));
    lineasVendidas += Math.max(lineas, 0);
    marcar();
  }

  /**
   * Suma una anulacion.
   *
   * @param monto total del pedido anulado
   */
  public void registrarAnulado(BigDecimal monto) {
    pedidosAnulados++;
    montoAnulado = montoAnulado.add(valor(monto));
    marcar();
  }

  /** Deja constancia de que el dia recibio un cambio de estado intermedio. */
  public void registrarCambioEstado() {
    marcar();
  }

  /**
   * Venta promedio por pedido confirmado.
   *
   * @return ticket promedio, cero si el dia no tiene confirmaciones
   */
  public BigDecimal ticketPromedio() {
    if (pedidosConfirmados == 0) {
      return BigDecimal.ZERO;
    }
    return montoConfirmado.divide(BigDecimal.valueOf(pedidosConfirmados), 0, RoundingMode.HALF_UP);
  }

  private static BigDecimal valor(BigDecimal monto) {
    return monto == null ? BigDecimal.ZERO : monto;
  }

  private void marcar() {
    actualizadoEn = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public LocalDate getFecha() {
    return fecha;
  }

  public int getPedidosCreados() {
    return pedidosCreados;
  }

  public int getPedidosConfirmados() {
    return pedidosConfirmados;
  }

  public int getPedidosAnulados() {
    return pedidosAnulados;
  }

  public BigDecimal getMontoCreado() {
    return montoCreado;
  }

  public BigDecimal getMontoConfirmado() {
    return montoConfirmado;
  }

  public BigDecimal getMontoAnulado() {
    return montoAnulado;
  }

  public int getLineasVendidas() {
    return lineasVendidas;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof MetricaDiaria otra && id != null && id.equals(otra.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
