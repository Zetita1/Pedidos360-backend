package cl.duoc.pedidos360.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Huella de una operacion sensible sobre un pedido: quien la hizo, cuando y por que.
 *
 * <p>Llega por el exchange direct, no por el de eventos: no es un aviso que se difunde, es
 * una orden con un unico responsable de ejecutarla.
 */
@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Size(max = 80)
  @Column(name = "evento_id", nullable = false, unique = true, length = 80)
  private String eventoId;

  @NotBlank
  @Size(max = 40)
  @Column(nullable = false, length = 40)
  private String operacion;

  @NotBlank
  @Size(max = 30)
  @Column(name = "pedido_numero", nullable = false, length = 30)
  private String pedidoNumero;

  @Column(name = "cliente_id")
  private Long clienteId;

  @Size(max = 64)
  @Column(name = "usuario_oid", length = 64)
  private String usuarioOid;

  @Size(max = 150)
  @Column(name = "usuario_nombre", length = 150)
  private String usuarioNombre;

  @Size(max = 30)
  @Column(name = "estado_anterior", length = 30)
  private String estadoAnterior;

  @Column(precision = 14, scale = 2)
  private BigDecimal monto;

  @Size(max = 300)
  @Column(length = 300)
  private String motivo;

  @Column(name = "ocurrido_en", nullable = false)
  private Instant ocurridoEn;

  @Column(name = "registrado_en", nullable = false)
  private Instant registradoEn;

  protected RegistroAuditoria() {}

  /**
   * Crea el registro a partir de un comando ya validado.
   *
   * @param eventoId identificador del comando de origen
   * @param operacion nombre de la operacion auditada
   * @param pedidoNumero correlativo del pedido
   * @param clienteId cliente afectado
   * @param usuarioOid identidad de quien ejecuto la operacion
   * @param usuarioNombre nombre de quien la ejecuto
   * @param estadoAnterior estado previo del pedido
   * @param monto monto involucrado
   * @param motivo justificacion
   * @param ocurridoEn momento del hecho
   */
  public RegistroAuditoria(
      String eventoId,
      String operacion,
      String pedidoNumero,
      Long clienteId,
      String usuarioOid,
      String usuarioNombre,
      String estadoAnterior,
      BigDecimal monto,
      String motivo,
      Instant ocurridoEn) {
    this.eventoId = eventoId;
    this.operacion = operacion;
    this.pedidoNumero = pedidoNumero;
    this.clienteId = clienteId;
    this.usuarioOid = usuarioOid;
    this.usuarioNombre = usuarioNombre;
    this.estadoAnterior = estadoAnterior;
    this.monto = monto;
    this.motivo = motivo;
    this.ocurridoEn = ocurridoEn;
  }

  @PrePersist
  void alCrear() {
    this.registradoEn = Instant.now();
    if (this.ocurridoEn == null) {
      this.ocurridoEn = this.registradoEn;
    }
  }

  public Long getId() {
    return id;
  }

  public String getEventoId() {
    return eventoId;
  }

  public String getOperacion() {
    return operacion;
  }

  public String getPedidoNumero() {
    return pedidoNumero;
  }

  public Long getClienteId() {
    return clienteId;
  }

  public String getUsuarioOid() {
    return usuarioOid;
  }

  public String getUsuarioNombre() {
    return usuarioNombre;
  }

  public String getEstadoAnterior() {
    return estadoAnterior;
  }

  public BigDecimal getMonto() {
    return monto;
  }

  public String getMotivo() {
    return motivo;
  }

  public Instant getOcurridoEn() {
    return ocurridoEn;
  }

  public Instant getRegistradoEn() {
    return registradoEn;
  }

  @Override
  public boolean equals(Object otro) {
    if (this == otro) {
      return true;
    }
    return otro instanceof RegistroAuditoria registro
        && id != null
        && Objects.equals(id, registro.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
