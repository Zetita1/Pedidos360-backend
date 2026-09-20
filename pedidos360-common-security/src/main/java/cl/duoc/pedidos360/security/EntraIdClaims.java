package cl.duoc.pedidos360.security;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/** Utilidades de lectura de los claims que emite Microsoft Entra ID (tokens v2.0). */
public final class EntraIdClaims {

  public static final String CLAIM_ROLES = "roles";
  public static final String CLAIM_SCOPE = "scp";
  public static final String CLAIM_OID = "oid";
  public static final String CLAIM_TENANT = "tid";
  public static final String CLAIM_NAME = "name";
  public static final String CLAIM_PREFERRED_USERNAME = "preferred_username";

  private EntraIdClaims() {}

  /**
   * Roles de aplicacion asignados al usuario o al servicio.
   *
   * @param jwt token ya validado
   * @return lista de roles, nunca {@code null}
   */
  public static List<String> roles(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList(CLAIM_ROLES);
    return roles == null ? List.of() : roles;
  }

  /**
   * Scopes delegados. Entra ID los entrega separados por espacio en el claim {@code scp}.
   *
   * @param jwt token ya validado
   * @return lista de scopes, nunca {@code null}
   */
  public static List<String> scopes(Jwt jwt) {
    String scp = jwt.getClaimAsString(CLAIM_SCOPE);
    if (scp == null || scp.isBlank()) {
      return List.of();
    }
    return List.of(scp.trim().split("\s+"));
  }

  /**
   * Identificador estable del usuario dentro del tenant (claim {@code oid}); si no viene,
   * se usa el {@code sub}.
   *
   * @param jwt token ya validado
   * @return identificador del sujeto
   */
  public static String userId(Jwt jwt) {
    String oid = jwt.getClaimAsString(CLAIM_OID);
    return oid != null ? oid : jwt.getSubject();
  }

  /**
   * Nombre legible del usuario para trazabilidad y auditoria.
   *
   * @param jwt token ya validado
   * @return nombre, correo o id del sujeto
   */
  public static String displayName(Jwt jwt) {
    String name = jwt.getClaimAsString(CLAIM_NAME);
    if (name != null && !name.isBlank()) {
      return name;
    }
    String upn = jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME);
    return upn != null ? upn : userId(jwt);
  }
}
