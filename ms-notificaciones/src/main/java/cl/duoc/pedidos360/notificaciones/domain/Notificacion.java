package cl.duoc.pedidos360.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;

/**
 * Aviso generado a partir de un evento de pedido.
 *
 * <p>Guarda el {@code eventoId} de origen para poder reconocer un mensaje repetido: RabbitMQ
 * garantiza "al menos una entrega", asi que un reintento puede traer dos veces el mismo
 * evento y la notificacion no debe duplicarse.
 */
@Entity
@Table(name = "notificacion")
public class Notificacion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Size(max = 80)
  @Column(name = "evento_id", nullable = false, unique = true, length = 80)
  private String eventoId;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoNotificacion tipo;

  @NotBlank
  @Size(max = 30)
  @Column(name = "pedido_numero", nullable = false, length = 30)
  private String pedidoNumero;

  @Column(name = "pedido_id")
  private Long pedidoId;

  @Column(name = "cliente_id")
  private Long clienteId;

  @Size(max = 150)
  @Column(length = 150)
  private String destinatario;

  @NotBlank
  @Size(max = 150)
  @Column(nullable = false, length = 150)
  private String asunto;

  @NotBlank
  @Size(max = 600)
  @Column(nullable = false, length = 600)
  private String mensaje;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected Notificacion() {}

  /**
   * Crea un aviso a partir de los datos de un evento ya validado.
   *
   * @param eventoId identificador del evento de origen
   * @param tipo motivo del aviso
   * @param pedidoNumero correlativo del pedido
   * @param pedidoId identificador interno del pedido
   * @param clienteId cliente destinatario
   * @param destinatario correo de contacto
   * @param asunto titulo del aviso
   * @param mensaje cuerpo del aviso
   */
  public Notificacion(
      String eventoId,
      TipoNotificacion tipo,
      String pedidoNumero,
      Long pedidoId,
      Long clienteId,
      String destinatario,
      String asunto,
      String mensaje) {
    this.eventoId = eventoId;
    this.tipo = tipo;
    this.pedidoNumero = pedidoNumero;
    this.pedidoId = pedidoId;
    this.clienteId = clienteId;
    this.destinatario = destinatario;
    this.asunto = asunto;
    this.mensaje = mensaje;
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

  public TipoNotificacion getTipo() {
    return tipo;
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

  public String getDestinatario() {
    return destinatario;
  }

  public String getAsunto() {
    return asunto;
  }

  public String getMensaje() {
    return mensaje;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  @Override
  public boolean equals(Object otro) {
    if (this == otro) {
      return true;
    }
    return otro instanceof Notificacion notificacion
        && id != null
        && Objects.equals(id, notificacion.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
