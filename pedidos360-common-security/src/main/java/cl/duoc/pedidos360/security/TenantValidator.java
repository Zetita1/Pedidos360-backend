package cl.duoc.pedidos360.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Verifica el claim {@code tid} para aceptar unicamente tokens emitidos por el tenant
 * de Microsoft Entra ID de la organizacion.
 */
public class TenantValidator implements OAuth2TokenValidator<Jwt> {

  private final String expectedTenantId;

  public TenantValidator(String expectedTenantId) {
    if (expectedTenantId == null || expectedTenantId.isBlank()) {
      throw new IllegalArgumentException("El tenant esperado no puede ser vacio");
    }
    this.expectedTenantId = expectedTenantId;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    String tid = token.getClaimAsString("tid");
    if (expectedTenantId.equals(tid)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "El token fue emitido por un tenant distinto al autorizado (tid=" + tid + ")",
            null));
  }
}
