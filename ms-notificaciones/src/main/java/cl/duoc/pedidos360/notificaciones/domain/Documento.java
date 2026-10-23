package cl.duoc.pedidos360.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Ticket del pedido confirmado: el documento que el consumidor genera de forma asincrona,
 * sin que el usuario tenga que esperarlo al confirmar.
 */
@Entity
@Table(name = "documento")
public class Documento {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Size(max = 80)
  @Column(name = "evento_id", nullable = false, unique = true, length = 80)
  private String eventoId;

  @NotBlank
  @Size(max = 30)
  @Column(nullable = false, unique = true, length = 30)
  private String folio;

  @NotBlank
  @Size(max = 30)
  @Column(name = "pedido_numero", nullable = false, length = 30)
  private String pedidoNumero;

  @Column(name = "pedido_id")
  private Long pedidoId;

  @Column(name = "cliente_id")
  private Long clienteId;

  @Size(max = 150)
  @Column(name = "cliente_nombre", length = 150)
  private String clienteNombre;

  @NotNull
  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal total;

  @NotBlank
  @Lob
  @Column(nullable = false)
  private String contenido;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected Documento() {}

  /**
   * Crea el ticket de un pedido confirmado.
   *
   * @param eventoId identificador del evento de origen
   * @param folio numero del ticket
   * @param pedidoNumero correlativo del pedido
   * @param pedidoId identificador interno del pedido
   * @param clienteId cliente dueno del pedido
   * @param clienteNombre nombre del cliente
   * @param total monto total del pedido
   * @param contenido ticket renderizado como texto
   */
  public Documento(
      String eventoId,
      String folio,
      String pedidoNumero,
      Long pedidoId,
      Long clienteId,
      String clienteNombre,
      BigDecimal total,
      String contenido) {
    this.eventoId = eventoId;
    this.folio = folio;
    this.pedidoNumero = pedidoNumero;
    this.pedidoId = pedidoId;
    this.clienteId = clienteId;
    this.clienteNombre = clienteNombre;
    this.total = total;
    this.contenido = contenido;
  }

  @PrePersist
  void alCrear() {
    this.creadoEn = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getEventoId() {
    return eventoId;
  }

  public String getFolio() {
    return folio;
  }

  public String getPedidoNumero() {
    return pedidoNumero;
  }

  public Long getPedidoId() {
    return pedidoId;
  }

  public Long getClienteId() {
    return clienteId;
  }

  public String getClienteNombre() {
    return clienteNombre;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public String getContenido() {
    return contenido;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  @Override
  public boolean equals(Object otro) {
    if (this == otro) {
      return true;
    }
    return otro instanceof Documento documento && id != null && Objects.equals(id, documento.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
