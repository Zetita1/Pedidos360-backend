package cl.duoc.pedidos360.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Responde 401 en formato Problem Detail (RFC 7807) cuando el token falta, esta vencido,
 * tiene firma invalida o no cumple issuer/audience.
 */
public class Pedidos360AuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public Pedidos360AuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {

    OAuth2Error error = extractError(exception);
    HttpStatus status =
        OAuth2ErrorCodes.INSUFFICIENT_SCOPE.equals(error.getErrorCode())
            ? HttpStatus.FORBIDDEN
            : HttpStatus.UNAUTHORIZED;

    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setType(URI.create("https://pedidos360.cl/errors/token-invalido"));
    problem.setTitle("Token no valido");
    problem.setDetail(
        error.getDescription() != null
            ? error.getDescription()
            : "Se requiere un access token valido emitido por el IDaaS configurado.");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("error", error.getErrorCode());
    problem.setProperty("timestamp", OffsetDateTime.now().toString());

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, buildChallenge(error));
    objectMapper.writeValue(response.getOutputStream(), problem);
  }

  private OAuth2Error extractError(AuthenticationException exception) {
    if (exception instanceof OAuth2AuthenticationException oauthException) {
      return oauthException.getError();
    }
    return new OAuth2Error(
        OAuth2ErrorCodes.INVALID_TOKEN, "No se recibio un token Bearer en la peticion.", null);
  }

  private String buildChallenge(OAuth2Error error) {
    StringBuilder challenge = new StringBuilder("Bearer realm=\"pedidos360\"");
    challenge.append(", error=\"").append(sanitize(error.getErrorCode())).append('"');
    if (error.getDescription() != null) {
      challenge
          .append(", error_description=\"")
          .append(sanitize(error.getDescription()))
          .append('"');
    }
    return challenge.toString();
  }

  private String sanitize(String value) {
    return value == null ? "" : value.replace("\"", "'").replace("\n", " ").replace("\r", " ");
  }
}
