package cl.duoc.pedidos360.pedidos.web;

import cl.duoc.pedidos360.pedidos.domain.EstadoPedido;
import cl.duoc.pedidos360.pedidos.service.PedidoService;
import cl.duoc.pedidos360.pedidos.web.PedidoDtos.CambioEstadoRequest;
import cl.duoc.pedidos360.pedidos.web.PedidoDtos.CrearPedidoRequest;
import cl.duoc.pedidos360.pedidos.web.PedidoDtos.PedidoResponse;
import cl.duoc.pedidos360.pedidos.web.PedidoDtos.ResumenEstado;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API REST de pedidos. Todos los endpoints exigen un JWT valido del IDaaS. */
@RestController
@RequestMapping("/api/v1/pedidos")
@Tag(name = "Pedidos", description = "Registro, confirmacion y seguimiento de pedidos")
@SecurityRequirement(name = "entra-id")
public class PedidoController {

  private final PedidoService service;

  public PedidoController(PedidoService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Lista pedidos filtrando por estado y/o cliente")
  public List<PedidoResponse> listar(
      @RequestParam(required = false) EstadoPedido estado,
      @RequestParam(required = false) Long clienteId) {
    return service.buscar(estado, clienteId).stream().map(PedidoResponse::de).toList();
  }

  @GetMapping("/mis-pedidos")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Pedidos registrados por el usuario autenticado")
  public List<PedidoResponse> misPedidos(@AuthenticationPrincipal Jwt jwt) {
    return service.misPedidos(EntraIdClaims.userId(jwt)).stream().map(PedidoResponse::de).toList();
  }

  @GetMapping("/resumen")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Cantidad de pedidos por estado para el tablero del frontend")
  public List<ResumenEstado> resumen() {
    return service.resumenPorEstado();
  }

  @GetMapping("/{id}")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Obtiene el detalle de un pedido")
  public PedidoResponse obtener(@PathVariable Long id) {
    return PedidoResponse.de(service.obtener(id));
  }

  @PostMapping
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Registra un pedido en estado BORRADOR con los precios vigentes")
  public ResponseEntity<PedidoResponse> crear(
      @Valid @RequestBody CrearPedidoRequest request, @AuthenticationPrincipal Jwt jwt) {
    PedidoResponse creado =
        PedidoResponse.de(
            service.crear(request, EntraIdClaims.userId(jwt), EntraIdClaims.displayName(jwt)));
    return ResponseEntity.created(URI.create("/api/v1/pedidos/" + creado.id())).body(creado);
  }

  @PostMapping("/{id}/confirmar")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Confirma el pedido y descuenta el stock en el catalogo")
  public PedidoResponse confirmar(@PathVariable Long id) {
    return PedidoResponse.de(service.confirmar(id));
  }

  @PutMapping("/{id}/estado")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Avanza el pedido dentro de su maquina de estados")
  public PedidoResponse cambiarEstado(
      @PathVariable Long id, @Valid @RequestBody CambioEstadoRequest request) {
    return PedidoResponse.de(service.cambiarEstado(id, request.estado(), request.motivo()));
  }

  @PutMapping("/{id}/anular")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Anula el pedido y repone el stock reservado (solo rol Pedidos.Admin)")
  public PedidoResponse anular(
      @PathVariable Long id, @RequestParam(required = false) String motivo) {
    return PedidoResponse.de(service.cambiarEstado(id, EstadoPedido.ANULADO, motivo));
  }
}
