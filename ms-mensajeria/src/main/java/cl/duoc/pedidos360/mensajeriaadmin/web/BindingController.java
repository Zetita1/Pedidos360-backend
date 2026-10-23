package cl.duoc.pedidos360.mensajeriaadmin.web;

import cl.duoc.pedidos360.mensajeriaadmin.service.RabbitAdminService;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.BindingRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.OperacionResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.TopologiaResponse;
import cl.duoc.pedidos360.security.SecurityRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administracion de bindings y consulta de la topologia que declara la aplicacion.
 *
 * <p>La topologia se expone de solo lectura: sirve para que el frontend muestre que cola
 * atiende cada evento sin tener que adivinarlo.
 */
@RestController
@RequestMapping("/api/v1/mensajeria")
@Tag(name = "Bindings", description = "Enlaces entre exchanges y colas, y topologia del sistema")
@SecurityRequirement(name = "entra-id")
public class BindingController {

  private final RabbitAdminService service;

  public BindingController(RabbitAdminService service) {
    this.service = service;
  }

  @PostMapping("/bindings")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Enlaza una cola a un exchange con una routing key")
  public OperacionResponse crear(@Valid @RequestBody BindingRequest peticion) {
    service.crearBinding(peticion);
    return new OperacionResponse(
        "crear-binding",
        peticion.exchange() + " -> " + peticion.cola(),
        "Enlazados con la routing key " + peticion.routingKey());
  }

  @DeleteMapping("/bindings")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Quita el enlace entre una cola y un exchange")
  public OperacionResponse eliminar(@Valid @RequestBody BindingRequest peticion) {
    service.eliminarBinding(peticion);
    return new OperacionResponse(
        "eliminar-binding",
        peticion.exchange() + " -> " + peticion.cola(),
        "Se quito la routing key " + peticion.routingKey());
  }

  @GetMapping("/topologia")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Topologia declarada por la aplicacion: exchanges, rutas y colas")
  public TopologiaResponse topologia() {
    return service.topologiaDelSistema();
  }
}
