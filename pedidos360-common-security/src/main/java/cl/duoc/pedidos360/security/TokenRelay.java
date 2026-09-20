package cl.duoc.pedidos360.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Recupera el access token con el que llego la peticion para reenviarlo a los microservicios
 * aguas abajo. Asi cada servicio vuelve a validar el JWT contra el IDaaS y no existen llamadas
 * internas anonimas.
 */
public final class TokenRelay {

  private TokenRelay() {}

  /**
   * Token del usuario autenticado en el contexto actual.
   *
   * @return valor del JWT, vacio si la llamada no esta autenticada con Bearer
   */
  public static Optional<String> tokenActual() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      return Optional.of(jwtAuthentication.getToken().getTokenValue());
    }
    return Optional.empty();
  }
}
