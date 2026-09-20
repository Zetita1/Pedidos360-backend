package cl.duoc.pedidos360.bff.web;

import cl.duoc.pedidos360.bff.model.BffModels.Tablero;
import cl.duoc.pedidos360.bff.service.TableroService;
import cl.duoc.pedidos360.security.SecurityRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tablero de inicio del SPA, armado por el BFF en una sola llamada. */
@RestController
@RequestMapping("/api/bff/tablero")
@Tag(name = "Tablero (BFF)", description = "Indicadores agregados para la pantalla de inicio")
@SecurityRequirement(name = "entra-id")
public class TableroController {

  private final TableroService service;

  public TableroController(TableroService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Indicadores de pedidos y catalogo para el tablero")
  public Tablero tablero() {
    return service.construir();
  }
}
