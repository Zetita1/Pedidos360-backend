package cl.duoc.pedidos360.clientes.web;

import cl.duoc.pedidos360.clientes.domain.Cliente;
import cl.duoc.pedidos360.clientes.domain.Direccion;
import cl.duoc.pedidos360.clientes.domain.SegmentoCliente;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Contratos de entrada y salida del API de clientes. */
public final class ClienteDtos {

  private ClienteDtos() {}

  /** Direccion de despacho en formato de transporte. */
  public record DireccionDto(
      @NotBlank @Size(max = 150) String calle,
      @Size(max = 20) String numero,
      @NotBlank @Size(max = 80) String comuna,
      @NotBlank @Size(max = 80) String ciudad,
      @Size(max = 80) String region) {

    public Direccion aDominio() {
      return new Direccion(calle, numero, comuna, ciudad, region);
    }

    public static DireccionDto de(Direccion direccion) {
      if (direccion == null) {
        return null;
      }
      return new DireccionDto(
          direccion.getCalle(),
          direccion.getNumero(),
          direccion.getComuna(),
          direccion.getCiudad(),
          direccion.getRegion());
    }
  }

  /** Representacion publica de un cliente. */
  public record ClienteResponse(
      Long id,
      String rut,
      String razonSocial,
      String email,
      String telefono,
      SegmentoCliente segmento,
      DireccionDto direccion,
      String direccionFormateada,
      boolean activo,
      Instant actualizadoEn) {

    public static ClienteResponse de(Cliente cliente) {
      return new ClienteResponse(
          cliente.getId(),
          cliente.getRut(),
          cliente.getRazonSocial(),
          cliente.getEmail(),
          cliente.getTelefono(),
          cliente.getSegmento(),
          DireccionDto.de(cliente.getDireccion()),
          cliente.getDireccion() == null ? null : cliente.getDireccion().formateada(),
          cliente.isActivo(),
          cliente.getActualizadoEn());
    }
  }

  /** Datos para dar de alta un cliente. */
  public record CrearClienteRequest(
      @NotBlank @Pattern(regexp = "^[0-9]{7,8}-[0-9kK]$", message = "El rut debe tener formato 12345678-9")
          String rut,
      @NotBlank @Size(max = 150) String razonSocial,
      @NotBlank @Email @Size(max = 150) String email,
      @Size(max = 20) String telefono,
      @NotNull SegmentoCliente segmento,
      @Valid @NotNull DireccionDto direccion) {}

  /** Datos editables de un cliente existente. */
  public record ActualizarClienteRequest(
      @NotBlank @Size(max = 150) String razonSocial,
      @NotBlank @Email @Size(max = 150) String email,
      @Size(max = 20) String telefono,
      @NotNull SegmentoCliente segmento,
      @Valid @NotNull DireccionDto direccion) {}
}
