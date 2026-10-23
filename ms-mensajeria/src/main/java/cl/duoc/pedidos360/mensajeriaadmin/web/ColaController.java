package cl.duoc.pedidos360.mensajeriaadmin.web;

import cl.duoc.pedidos360.mensajeriaadmin.service.RabbitAdminService;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.ColaResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.MensajeriaDtos.CrearColaRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administracion de colas.
 *
 * <p>Crear y eliminar colas es una operacion de infraestructura: solo el rol administrador
 * puede hacerlo, y la topologia que usa la aplicacion queda protegida.
 */
@RestController
@RequestMapping("/api/v1/mensajeria/colas")
@Tag(name = "Colas", description = "Creacion, consulta y eliminacion de colas de RabbitMQ")
@SecurityRequirement(name = "entra-id")
public class ColaController {

  private final RabbitAdminService service;

  public ColaController(RabbitAdminService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Lista las colas del broker con sus mensajes y consumidores")
  public List<ColaResponse> listar() {
    return service.listarColas();
  }

  @GetMapping("/{nombre}")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Obtiene el estado de una cola")
  public ColaResponse obtener(@PathVariable String nombre) {
    return service.obtenerCola(nombre);
  }

  @PostMapping
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Crea una cola, opcionalmente junto a su DLQ")
  public ResponseEntity<ColaResponse> crear(@Valid @RequestBody CrearColaRequest peticion) {
    ColaResponse creada = service.crearCola(peticion);
    return ResponseEntity.created(URI.create("/api/v1/mensajeria/colas/" + creada.nombre()))
        .body(creada);
  }

  @PostMapping("/{nombre}/purgar")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Vacia una cola sin eliminarla")
  public ColaResponse purgar(@PathVariable String nombre) {
    return service.purgarCola(nombre);
  }

  @DeleteMapping("/{nombre}")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Elimina una cola que no pertenezca a la topologia del sistema")
  public OperacionResponse eliminar(
      @PathVariable String nombre,
      @RequestParam(defaultValue = "false") boolean soloSiNoSeUsa,
      @RequestParam(defaultValue = "false") boolean soloSiVacia) {
    service.eliminarCola(nombre, soloSiNoSeUsa, soloSiVacia);
    return new OperacionResponse("eliminar-cola", nombre, "La cola fue eliminada del broker");
  }
}
