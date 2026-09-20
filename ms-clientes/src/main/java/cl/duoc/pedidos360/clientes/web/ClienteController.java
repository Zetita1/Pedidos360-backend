package cl.duoc.pedidos360.clientes.web;

import cl.duoc.pedidos360.clientes.service.ClienteService;
import cl.duoc.pedidos360.clientes.web.ClienteDtos.ActualizarClienteRequest;
import cl.duoc.pedidos360.clientes.web.ClienteDtos.ClienteResponse;
import cl.duoc.pedidos360.clientes.web.ClienteDtos.CrearClienteRequest;
import cl.duoc.pedidos360.security.EntraIdClaims;
import cl.duoc.pedidos360.security.SecurityRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API REST de clientes. Todos los endpoints exigen un JWT valido del IDaaS. */
@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes", description = "Fichas de clientes y direcciones de despacho")
@SecurityRequirement(name = "entra-id")
public class ClienteController {

  private final ClienteService service;

  public ClienteController(ClienteService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Lista clientes (roles Pedidos.Admin o Pedidos.Vendedor)")
  public List<ClienteResponse> listar(
      @RequestParam(required = false) String buscar,
      @RequestParam(defaultValue = "true") boolean soloActivos) {
    return service.buscar(buscar, soloActivos).stream().map(ClienteResponse::de).toList();
  }

  @GetMapping("/mi-ficha")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Devuelve la ficha del usuario autenticado, resuelta desde el claim oid")
  public ClienteResponse miFicha(@AuthenticationPrincipal Jwt jwt) {
    return ClienteResponse.de(service.obtenerPorUsuario(EntraIdClaims.userId(jwt)));
  }

  @GetMapping("/{id}")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Obtiene un cliente por su identificador")
  public ClienteResponse obtener(@PathVariable Long id) {
    return ClienteResponse.de(service.obtener(id));
  }

  @GetMapping("/rut/{rut}")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Obtiene un cliente por su rut")
  public ClienteResponse obtenerPorRut(@PathVariable String rut) {
    return ClienteResponse.de(service.obtenerPorRut(rut));
  }

  @PostMapping
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Crea una ficha de cliente")
  public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody CrearClienteRequest request) {
    ClienteResponse creado = ClienteResponse.de(service.crear(request, null));
    return ResponseEntity.created(URI.create("/api/v1/clientes/" + creado.id())).body(creado);
  }

  @PutMapping("/{id}")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Actualiza los datos de un cliente")
  public ClienteResponse actualizar(
      @PathVariable Long id, @Valid @RequestBody ActualizarClienteRequest request) {
    return ClienteResponse.de(service.actualizar(id, request));
  }

  @PutMapping("/{id}/vincular-usuario")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Asocia la ficha a un usuario del IDaaS (claim oid)")
  public ClienteResponse vincularUsuario(
      @PathVariable Long id, @RequestParam("oid") String usuarioOid) {
    return ClienteResponse.de(service.vincularUsuario(id, usuarioOid));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Da de baja logica un cliente (solo rol Pedidos.Admin)")
  public ResponseEntity<Void> desactivar(@PathVariable Long id) {
    service.desactivar(id);
    return ResponseEntity.noContent().build();
  }
}
