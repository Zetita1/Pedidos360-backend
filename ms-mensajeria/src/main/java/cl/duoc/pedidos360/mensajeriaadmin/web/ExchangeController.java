package cl.duoc.pedidos360.mensajeriaadmin.web;

import cl.duoc.pedidos360.mensajeriaadmin.service.RabbitAdminService;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.CrearExchangeRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.ExchangeResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.OperacionResponse;
import cl.duoc.pedidos360.security.SecurityRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administracion de exchanges. */
@RestController
@RequestMapping("/api/v1/mensajeria/exchanges")
@Tag(name = "Exchanges", description = "Creacion, consulta y eliminacion de exchanges de RabbitMQ")
@SecurityRequirement(name = "entra-id")
public class ExchangeController {

  private final RabbitAdminService service;

  public ExchangeController(RabbitAdminService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Lista los exchanges declarados en el broker")
  public List<ExchangeResponse> listar() {
    return service.listarExchanges();
  }

  @PostMapping
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Crea un exchange de tipo direct, topic, fanout o headers")
  public ResponseEntity<ExchangeResponse> crear(
      @Valid @RequestBody CrearExchangeRequest peticion) {
    ExchangeResponse creado = service.crearExchange(peticion);
    return ResponseEntity.created(
            URI.create("/api/v1/mensajeria/exchanges/" + creado.nombre()))
        .body(creado);
  }

  @DeleteMapping("/{nombre}")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Elimina un exchange que no pertenezca a la topologia del sistema")
  public OperacionResponse eliminar(@PathVariable String nombre) {
    service.eliminarExchange(nombre);
    return new OperacionResponse(
        "eliminar-exchange", nombre, "El exchange fue eliminado del broker");
  }
}
