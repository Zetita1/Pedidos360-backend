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
 * Acumulado de ventas por cliente.
 *
 * <p>Se construye con los datos que trae el propio evento. Es la razon por la que el evento
 * lleva el nombre del cliente y no solo su id: si hubiera que resolverlo consultando a
 * ms-clientes, la analitica dependeria de que ese microservicio estuviera arriba y el
 * procesamiento dejaria de ser independiente.
 */
@Entity
@Table(name = "venta_por_cliente")
public class VentaPorCliente {

  /** El id del cliente es la clave: hay una fila por cliente, que se va actualizando. */
  @Id
  @Column(name = "cliente_id")
  private Long clienteId;

  @Column(name = "cliente_nombre", length = 180)
  private String clienteNombre;

  @Column(name = "pedidos_confirmados", nullable = false)
  private int pedidosConfirmados;

  @Column(name = "monto_total", nullable = false, precision = 15, scale = 2)
  private BigDecimal montoTotal = BigDecimal.ZERO;

  @Column(name = "ultima_compra_en")
  private Instant ultimaCompraEn;

  @Version private Long version;

  protected VentaPorCliente() {}

  /**
   * @param clienteId identificador del cliente
   * @param clienteNombre razon social del cliente
   */
  public VentaPorCliente(Long clienteId, String clienteNombre) {
    this.clienteId = clienteId;
    this.clienteNombre = clienteNombre;
  }

  /**
   * Suma una compra confirmada del cliente.
   *
   * @param monto total del pedido
   * @param ocurridoEn momento del hecho
   * @param clienteNombre nombre con el que llego el evento; actualiza el guardado
   */
  public void sumar(BigDecimal monto, Instant ocurridoEn, String clienteNombre) {
    pedidosConfirmados++;
    montoTotal = montoTotal.add(monto == null ? BigDecimal.ZERO : monto);
    if (ultimaCompraEn == null || (ocurridoEn != null && ocurridoEn.isAfter(ultimaCompraEn))) {
      ultimaCompraEn = ocurridoEn;
    }
    if (clienteNombre != null && !clienteNombre.isBlank()) {
      this.clienteNombre = clienteNombre;
    }
  }

  public Long getClienteId() {
    return clienteId;
  }

  public String getClienteNombre() {
    return clienteNombre;
  }

  public int getPedidosConfirmados() {
    return pedidosConfirmados;
  }

  public BigDecimal getMontoTotal() {
    return montoTotal;
  }

  public Instant getUltimaCompraEn() {
    return ultimaCompraEn;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof VentaPorCliente otra
        && clienteId != null
        && clienteId.equals(otra.clienteId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(clienteId);
  }
}
