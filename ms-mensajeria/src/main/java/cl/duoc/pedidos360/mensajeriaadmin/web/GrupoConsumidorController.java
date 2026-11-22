package cl.duoc.pedidos360.mensajeriaadmin.web;

import cl.duoc.pedidos360.mensajeriaadmin.service.KafkaAdminService;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.GrupoDetalleResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.GrupoResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.OperacionKafkaResponse;
import cl.duoc.pedidos360.security.SecurityRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supervision de los grupos de consumidores.
 *
 * <p>Es la vista de operacion del streaming: cuantos consumidores tiene cada grupo, que
 * particion atiende cada uno, hasta donde leyeron y cuanto les falta. Con eso se distingue un
 * sistema que va al dia de uno que se esta quedando atras, antes de que el usuario lo note.
 */
@RestController
@RequestMapping("/api/v1/streaming/grupos")
@Tag(name = "Grupos de consumidores", description = "Offsets, lag y distribucion del consumo")
@SecurityRequirement(name = "entra-id")
public class GrupoConsumidorController {

  private final KafkaAdminService service;

  public GrupoConsumidorController(KafkaAdminService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Lista los grupos con su estado, miembros y lag total")
  public List<GrupoResponse> listar() {
    return service.listarGrupos();
  }

  @GetMapping("/{grupo}")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Avance de un grupo particion por particion, con offsets y lag")
  public GrupoDetalleResponse obtener(@PathVariable String grupo) {
    return service.obtenerGrupo(grupo);
  }

  @DeleteMapping("/{grupo}")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Elimina un grupo que no sea de la aplicacion, descartando su avance")
  public OperacionKafkaResponse eliminar(@PathVariable String grupo) {
    service.eliminarGrupo(grupo);
    return new OperacionKafkaResponse(
        "eliminar-grupo", grupo, "El grupo y su avance fueron eliminados del cluster");
  }
}
