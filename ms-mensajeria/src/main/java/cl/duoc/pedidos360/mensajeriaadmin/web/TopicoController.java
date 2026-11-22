package cl.duoc.pedidos360.mensajeriaadmin.web;

import cl.duoc.pedidos360.mensajeriaadmin.service.KafkaAdminService;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.AmpliarParticionesRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.ClusterResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.ConfigurarTopicoRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.CrearTopicoRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.OperacionKafkaResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.TopicoDetalleResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.TopicoResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administracion de topicos de Kafka.
 *
 * <p>Consultar es una operacion de gestion; crear, reconfigurar y eliminar es de
 * infraestructura, y solo la puede hacer el rol administrador. La topologia que usa la
 * aplicacion queda protegida contra borrado.
 */
@RestController
@RequestMapping("/api/v1/streaming/topicos")
@Tag(name = "Topicos", description = "Creacion, configuracion y eliminacion de topicos de Kafka")
@SecurityRequirement(name = "entra-id")
public class TopicoController {

  private final KafkaAdminService service;

  public TopicoController(KafkaAdminService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Lista los topicos con sus particiones, replicas y politica de limpieza")
  public List<TopicoResponse> listar(
      @RequestParam(defaultValue = "false") boolean incluirInternos) {
    return service.listarTopicos(incluirInternos);
  }

  @GetMapping("/cluster")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Estado del cluster: brokers, controlador y particiones sin replicar")
  public ClusterResponse cluster() {
    return service.cluster();
  }

  @GetMapping("/{nombre}")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Detalle de un topico, con el lider y las replicas de cada particion")
  public TopicoDetalleResponse obtener(@PathVariable String nombre) {
    return service.obtenerTopico(nombre);
  }

  @PostMapping
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Crea un topico con sus particiones, replicas y configuraciones")
  public ResponseEntity<TopicoDetalleResponse> crear(
      @Valid @RequestBody CrearTopicoRequest peticion) {
    TopicoDetalleResponse creado = service.crearTopico(peticion);
    return ResponseEntity.created(URI.create("/api/v1/streaming/topicos/" + creado.nombre()))
        .body(creado);
  }

  @PatchMapping("/{nombre}/configuracion")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Cambia parametros de un topico, como retention.ms o cleanup.policy")
  public TopicoDetalleResponse configurar(
      @PathVariable String nombre, @Valid @RequestBody ConfigurarTopicoRequest peticion) {
    return service.configurarTopico(nombre, peticion);
  }

  @PostMapping("/{nombre}/particiones")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Amplia las particiones de un topico; Kafka solo permite aumentarlas")
  public TopicoDetalleResponse ampliar(
      @PathVariable String nombre, @Valid @RequestBody AmpliarParticionesRequest peticion) {
    return service.ampliarParticiones(nombre, peticion);
  }

  @DeleteMapping("/{nombre}")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Elimina un topico que no pertenezca a la topologia del sistema")
  public OperacionKafkaResponse eliminar(@PathVariable String nombre) {
    service.eliminarTopico(nombre);
    return new OperacionKafkaResponse(
        "eliminar-topico", nombre, "El topico y sus registros fueron eliminados del cluster");
  }
}
