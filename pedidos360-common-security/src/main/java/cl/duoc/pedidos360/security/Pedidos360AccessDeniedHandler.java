package cl.duoc.pedidos360.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Responde 403 en formato Problem Detail cuando el token es valido pero el usuario no
 * tiene el rol o scope exigido por el endpoint.
 */
public class Pedidos360AccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public Pedidos360AccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setType(URI.create("https://pedidos360.cl/errors/acceso-denegado"));
    problem.setTitle("Acceso denegado");
    problem.setDetail("El token es valido pero no posee el rol o scope necesario para esta operacion.");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("error", "insufficient_permissions");
    problem.setProperty("timestamp", OffsetDateTime.now().toString());

    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
