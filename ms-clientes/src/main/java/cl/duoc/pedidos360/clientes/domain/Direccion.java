package cl.duoc.pedidos360.clientes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Direccion de despacho asociada a un cliente. */
@Embeddable
public class Direccion {

  @NotBlank
  @Size(max = 150)
  @Column(name = "calle", length = 150)
  private String calle;

  @Size(max = 20)
  @Column(name = "numero", length = 20)
  private String numero;

  @NotBlank
  @Size(max = 80)
  @Column(name = "comuna", length = 80)
  private String comuna;

  @NotBlank
  @Size(max = 80)
  @Column(name = "ciudad", length = 80)
  private String ciudad;

  @Size(max = 80)
  @Column(name = "region", length = 80)
  private String region;

  protected Direccion() {
    // requerido por JPA
  }

  public Direccion(String calle, String numero, String comuna, String ciudad, String region) {
    this.calle = calle;
    this.numero = numero;
    this.comuna = comuna;
    this.ciudad = ciudad;
    this.region = region;
  }

  public String getCalle() {
    return calle;
  }

  public String getNumero() {
    return numero;
  }

  public String getComuna() {
    return comuna;
  }

  public String getCiudad() {
    return ciudad;
  }

  public String getRegion() {
    return region;
  }

  /**
   * Direccion en una sola linea para mostrar en el frontend.
   *
   * @return direccion formateada
   */
  public String formateada() {
    StringBuilder sb = new StringBuilder(calle == null ? "" : calle);
    if (numero != null && !numero.isBlank()) {
      sb.append(' ').append(numero);
    }
    if (comuna != null && !comuna.isBlank()) {
      sb.append(", ").append(comuna);
    }
    if (ciudad != null && !ciudad.isBlank()) {
      sb.append(", ").append(ciudad);
    }
    return sb.toString();
  }
}
