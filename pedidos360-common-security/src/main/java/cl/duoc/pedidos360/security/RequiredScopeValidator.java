package cl.duoc.pedidos360.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Exige que el access token delegado incluya los scopes minimos de la API
 * (claim {@code scp} en tokens v2.0 de Microsoft Entra ID).
 */
public class RequiredScopeValidator implements OAuth2TokenValidator<Jwt> {

  private final List<String> requiredScopes;

  public RequiredScopeValidator(List<String> requiredScopes) {
    this.requiredScopes = List.copyOf(requiredScopes);
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    List<String> granted = EntraIdClaims.scopes(token);
    List<String> missing = requiredScopes.stream().filter(s -> !granted.contains(s)).toList();
    if (missing.isEmpty()) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INSUFFICIENT_SCOPE,
            "El token no incluye los scopes requeridos: " + missing,
            "https://tools.ietf.org/html/rfc6750#section-3.1"));
  }
}
