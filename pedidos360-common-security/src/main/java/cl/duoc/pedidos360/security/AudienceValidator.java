package cl.duoc.pedidos360.security;

import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.CollectionUtils;

/**
 * Verifica que el claim {@code aud} del token contenga alguna de las audiencias declaradas
 * por el recurso. Sin esta validacion un token valido emitido para otra API del mismo tenant
 * seria aceptado (ataque de "token replay" entre aplicaciones).
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private final List<String> expectedAudiences;

  public AudienceValidator(List<String> expectedAudiences) {
    if (CollectionUtils.isEmpty(expectedAudiences)) {
      throw new IllegalArgumentException("Debe declarar al menos una audiencia esperada");
    }
    this.expectedAudiences = List.copyOf(expectedAudiences);
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    List<String> audiences = token.getAudience();
    if (!CollectionUtils.isEmpty(audiences)
        && audiences.stream().filter(Objects::nonNull).anyMatch(expectedAudiences::contains)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "La audiencia del token no corresponde a este recurso. Esperado: "
                + expectedAudiences
                + ", recibido: "
                + audiences,
            "https://tools.ietf.org/html/rfc6750#section-3.1"));
  }
}
