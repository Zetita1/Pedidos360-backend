package cl.duoc.pedidos360.pedidos.domain;

import cl.duoc.pedidos360.web.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Pedido de un cliente, con su detalle y su maquina de estados. */
@Entity
@Table(name = "pedido")
public class Pedido {

  /** IVA vigente en Chile, aplicado sobre el neto de las lineas. */
  public static final BigDecimal TASA_IVA = new BigDecimal("0.19");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Column(nullable = false, unique = true, length = 20)
  private String numero;

  @NotNull
  @Column(name = "cliente_id", nullable = false)
  private Long clienteId;

  @Column(name = "cliente_rut", nullable = false, length = 12)
  private String clienteRut;

  @Column(name = "cliente_razon_social", nullable = false, length = 150)
  private String clienteRazonSocial;

  /** Usuario del IDaaS que registro el pedido (claim oid). */
  @Column(name = "usuario_oid", nullable = false, length = 64)
  private String usuarioOid;

  @Column(name = "usuario_nombre", length = 150)
  private String usuarioNombre;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoPedido estado = EstadoPedido.BORRADOR;

  @Size(max = 250)
  @Column(name = "direccion_despacho", length = 250)
  private String direccionDespacho;

  @Size(max = 300)
  @Column(length = 300)
  private String observacion;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal neto = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal iva = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal total = BigDecimal.ZERO;

  @OneToMany(
      mappedBy = "pedido",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private List<DetallePedido> detalles = new ArrayList<>();

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  protected Pedido() {
    // requerido por JPA
  }

  public Pedido(
      String numero,
      Long clienteId,
      String clienteRut,
      String clienteRazonSocial,
      String usuarioOid,
      String usuarioNombre,
      String direccionDespacho,
      String observacion) {
    this.numero = numero;
    this.clienteId = clienteId;
    this.clienteRut = clienteRut;
    this.clienteRazonSocial = clienteRazonSocial;
    this.usuarioOid = usuarioOid;
    this.usuarioNombre = usuarioNombre;
    this.direccionDespacho = direccionDespacho;
    this.observacion = observacion;
    this.estado = EstadoPedido.BORRADOR;
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
   * Agrega una linea al pedido y recalcula los totales.
   *
   * @param detalle linea a agregar
   */
  public void agregarDetalle(DetallePedido detalle) {
    if (estado != EstadoPedido.BORRADOR) {
      throw new ReglaNegocioException(
          "Solo se pueden modificar las lineas de un pedido en estado BORRADOR");
    }
    detalle.asociarA(this);
    this.detalles.add(detalle);
    recalcularTotales();
  }

  /** Recalcula neto, IVA y total a partir del detalle. */
  public void recalcularTotales() {
    BigDecimal sumaNeta =
        detalles.stream()
            .map(DetallePedido::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    this.neto = sumaNeta;
    this.iva = sumaNeta.multiply(TASA_IVA).setScale(2, RoundingMode.HALF_UP);
    this.total = this.neto.add(this.iva);
  }

  /**
   * Aplica una transicion de estado validando la maquina de estados.
   *
   * @param destino nuevo estado
   */
  public void cambiarEstado(EstadoPedido destino) {
    if (destino == estado) {
      return;
    }
    if (!estado.puedeAvanzarA(destino)) {
      throw new ReglaNegocioException(
          "No es posible pasar el pedido " + numero + " de " + estado + " a " + destino);
    }
    this.estado = destino;
  }

  /** Confirma el pedido dejandolo listo para preparacion. */
  public void confirmar() {
    if (detalles.isEmpty()) {
      throw new ReglaNegocioException("No se puede confirmar un pedido sin lineas de detalle");
    }
    cambiarEstado(EstadoPedido.CONFIRMADO);
  }

  /** Anula el pedido. */
  public void anular() {
    cambiarEstado(EstadoPedido.ANULADO);
  }

  public void actualizarDespacho(String direccionDespacho, String observacion) {
    if (estado == EstadoPedido.ENTREGADO || estado == EstadoPedido.ANULADO) {
      throw new ReglaNegocioException("El pedido ya esta cerrado y no admite cambios");
    }
    this.direccionDespacho = direccionDespacho;
    this.observacion = observacion;
  }

  public Long getId() {
    return id;
  }

  public String getNumero() {
    return numero;
  }

  public Long getClienteId() {
    return clienteId;
  }

  public String getClienteRut() {
    return clienteRut;
  }

  public String getClienteRazonSocial() {
    return clienteRazonSocial;
  }

  public String getUsuarioOid() {
    return usuarioOid;
  }

  public String getUsuarioNombre() {
    return usuarioNombre;
  }

  public EstadoPedido getEstado() {
    return estado;
  }

  public String getDireccionDespacho() {
    return direccionDespacho;
  }

  public String getObservacion() {
    return observacion;
  }

  public BigDecimal getNeto() {
    return neto;
  }

  public BigDecimal getIva() {
    return iva;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public List<DetallePedido> getDetalles() {
    return Collections.unmodifiableList(detalles);
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Pedido otro)) {
      return false;
    }
    return id != null && Objects.equals(id, otro.id);
  }

  @Override
  public int hashCode() {
    return Pedido.class.hashCode();
  }
}
