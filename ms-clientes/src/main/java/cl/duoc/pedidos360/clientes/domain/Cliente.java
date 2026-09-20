package cl.duoc.pedidos360.clientes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;

/**
 * Cliente de Pedidos360. El campo {@code usuarioOid} enlaza la ficha con la identidad del
 * IDaaS (claim {@code oid}) para que un usuario con rol Pedidos.Cliente solo vea lo suyo.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Size(max = 12)
  @Column(nullable = false, unique = true, length = 12)
  private String rut;

  @NotBlank
  @Size(max = 150)
  @Column(name = "razon_social", nullable = false, length = 150)
  private String razonSocial;

  @Email
  @NotBlank
  @Size(max = 150)
  @Column(nullable = false, length = 150)
  private String email;

  @Size(max = 20)
  @Column(length = 20)
  private String telefono;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SegmentoCliente segmento = SegmentoCliente.MINORISTA;

  @Valid
  @Embedded
  private Direccion direccion;

  /** Identificador del usuario en Microsoft Entra ID (claim oid). */
  @Size(max = 64)
  @Column(name = "usuario_oid", length = 64)
  private String usuarioOid;

  @Column(nullable = false)
  private boolean activo = true;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  protected Cliente() {
    // requerido por JPA
  }

  public Cliente(
      String rut,
      String razonSocial,
      String email,
      String telefono,
      SegmentoCliente segmento,
      Direccion direccion,
      String usuarioOid) {
    this.rut = rut;
    this.razonSocial = razonSocial;
    this.email = email;
    this.telefono = telefono;
    this.segmento = segmento == null ? SegmentoCliente.MINORISTA : segmento;
    this.direccion = direccion;
    this.usuarioOid = usuarioOid;
    this.activo = true;
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

  public void actualizarDatos(
      String razonSocial,
      String email,
      String telefono,
      SegmentoCliente segmento,
      Direccion direccion) {
    this.razonSocial = razonSocial;
    this.email = email;
    this.telefono = telefono;
    this.segmento = segmento == null ? this.segmento : segmento;
    this.direccion = direccion;
  }

  public void vincularUsuario(String oid) {
    this.usuarioOid = oid;
  }

  public void desactivar() {
    this.activo = false;
  }

  public Long getId() {
    return id;
  }

  public String getRut() {
    return rut;
  }

  public String getRazonSocial() {
    return razonSocial;
  }

  public String getEmail() {
    return email;
  }

  public String getTelefono() {
    return telefono;
  }

  public SegmentoCliente getSegmento() {
    return segmento;
  }

  public Direccion getDireccion() {
    return direccion;
  }

  public String getUsuarioOid() {
    return usuarioOid;
  }

  public boolean isActivo() {
    return activo;
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
    if (!(obj instanceof Cliente otro)) {
      return false;
    }
    return id != null && Objects.equals(id, otro.id);
  }

  @Override
  public int hashCode() {
    return Cliente.class.hashCode();
  }
}
