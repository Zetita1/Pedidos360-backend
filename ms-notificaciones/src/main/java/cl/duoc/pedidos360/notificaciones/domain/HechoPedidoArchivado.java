package cl.duoc.pedidos360.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Copia inalterable de un hecho de pedido, tal como venia en el topico de Kafka.
 *
 * <p>Es un archivo, no una proyeccion: no agrega ni resume nada, guarda cada registro como
 * llego. Las metricas de la analitica se pueden recalcular en cualquier momento; esto no se
 * recalcula, se conserva, y es lo que permite responder despues que paso con un pedido
 * concreto y en que orden.
 */
@Entity
@Table(name = "hecho_pedido_archivado")
public class HechoPedidoArchivado {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "evento_id", nullable = false, unique = true, length = 60)
  private String eventoId;

  @Column(nullable = false, length = 40)
  private String tipo;

  @Column(name = "pedido_numero", nullable = false, length = 40)
  private String pedidoNumero;

  @Column(name = "cliente_id")
  private Long clienteId;

  @Column(name = "cliente_nombre", length = 180)
  private String clienteNombre;

  @Column(precision = 15, scale = 2)
  private BigDecimal total;

  @Column(name = "estado_anterior", length = 30)
  private String estadoAnterior;

  @Column(name = "estado_nuevo", length = 30)
  private String estadoNuevo;

  @Column(length = 240)
  private String motivo;

  @Column(name = "usuario_oid", length = 60)
  private String usuarioOid;

  /** Topico del que se leyo, parte de las coordenadas del registro. */
  @Column(nullable = false, length = 120)
  private String topico;

  @Column(nullable = false)
  private int particion;

  /** La columna se llama desplazamiento porque OFFSET es palabra reservada en SQL. */
  @Column(name = "desplazamiento", nullable = false)
  private long offset;

  @Column(name = "ocurrido_en", nullable = false)
  private Instant ocurridoEn;

  @Column(name = "archivado_en", nullable = false)
  private Instant archivadoEn = Instant.now();

  protected HechoPedidoArchivado() {}

  /**
   * @param eventoId identificador del evento
   * @param tipo hecho que traia el registro
   * @param pedidoNumero correlativo del pedido
   * @param clienteId identificador del cliente
   * @param clienteNombre razon social del cliente
   * @param total monto del pedido
   * @param estadoAnterior estado previo
   * @param estadoNuevo estado resultante
   * @param motivo justificacion del cambio
   * @param usuarioOid identidad de quien lo provoco
   * @param topico topico de origen
   * @param particion particion de origen
   * @param offset posicion en la particion
   * @param ocurridoEn momento del hecho
   */
  public HechoPedidoArchivado(
      String eventoId,
      String tipo,
      String pedidoNumero,
      Long clienteId,
      String clienteNombre,
      BigDecimal total,
      String estadoAnterior,
      String estadoNuevo,
      String motivo,
      String usuarioOid,
      String topico,
      int particion,
      long offset,
      Instant ocurridoEn) {
    this.eventoId = eventoId;
    this.tipo = tipo;
    this.pedidoNumero = pedidoNumero;
    this.clienteId = clienteId;
    this.clienteNombre = clienteNombre;
    this.total = total;
    this.estadoAnterior = estadoAnterior;
    this.estadoNuevo = estadoNuevo;
    this.motivo = motivo;
    this.usuarioOid = usuarioOid;
    this.topico = topico;
    this.particion = particion;
    this.offset = offset;
    this.ocurridoEn = ocurridoEn;
  }

  public Long getId() {
    return id;
  }

  public String getEventoId() {
    return eventoId;
  }

  public String getTipo() {
    return tipo;
  }

  public String getPedidoNumero() {
    return pedidoNumero;
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

  public String getEstadoAnterior() {
    return estadoAnterior;
  }

  public String getEstadoNuevo() {
    return estadoNuevo;
  }

  public String getMotivo() {
    return motivo;
  }

  public String getUsuarioOid() {
    return usuarioOid;
  }

  public String getTopico() {
    return topico;
  }

  public int getParticion() {
    return particion;
  }

  public long getOffset() {
    return offset;
  }

  public Instant getOcurridoEn() {
    return ocurridoEn;
  }

  public Instant getArchivadoEn() {
    return archivadoEn;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof HechoPedidoArchivado otro && id != null && id.equals(otro.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
