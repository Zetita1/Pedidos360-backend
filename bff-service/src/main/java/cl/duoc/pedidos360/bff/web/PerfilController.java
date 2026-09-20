package cl.duoc.pedidos360.bff.web;

import cl.duoc.pedidos360.bff.model.BffModels.Permisos;
import cl.duoc.pedidos360.bff.model.BffModels.PerfilUsuario;
import cl.duoc.pedidos360.security.EntraIdClaims;
import cl.duoc.pedidos360.security.SecurityRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Devuelve al SPA la identidad y los permisos derivados del token. El frontend no decide
 * nada por su cuenta: lee del token lo mismo que valida el backend.
 */
@RestController
@RequestMapping("/api/bff/perfil")
@Tag(name = "Perfil", description = "Identidad y permisos del usuario autenticado")
@SecurityRequirement(name = "entra-id")
public class PerfilController {

  @GetMapping
  @Operation(summary = "Perfil del usuario resuelto desde los claims del JWT")
  public PerfilUsuario perfil(@AuthenticationPrincipal Jwt jwt) {
    List<String> roles = EntraIdClaims.roles(jwt);
    List<String> scopes = EntraIdClaims.scopes(jwt);

    boolean admin = roles.contains(SecurityRoles.ADMIN);
    boolean vendedor = roles.contains(SecurityRoles.VENDEDOR);

    Permisos permisos =
        new Permisos(admin, admin || vendedor, admin || vendedor, admin, admin || vendedor);

    return new PerfilUsuario(
        EntraIdClaims.userId(jwt),
        EntraIdClaims.displayName(jwt),
        jwt.getClaimAsString(EntraIdClaims.CLAIM_PREFERRED_USERNAME),
        jwt.getClaimAsString(EntraIdClaims.CLAIM_TENANT),
        roles,
        scopes,
        permisos);
  }
}
