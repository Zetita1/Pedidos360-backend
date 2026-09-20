package cl.duoc.pedidos360.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * Traduce las excepciones de la aplicacion a respuestas Problem Detail (RFC 7807) para que
 * el frontend reciba siempre el mismo contrato de error.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /**
   * Se declara explicitamente para que la denegacion lanzada por {@code @PreAuthorize} no
   * termine capturada por el manejador generico y devuelva 500 en vez de 403.
   *
   * @param ex excepcion de autorizacion de Spring Security
   * @param request peticion en curso
   * @return problema 403
   */
  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ProblemDetail handleAccesoDenegado(
      AccessDeniedException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Acceso denegado",
        "El token es valido pero no posee el rol o scope necesario para esta operacion.",
        request);
  }

  /**
   * Cubre los casos en que la autenticacion falla dentro del controlador (por ejemplo, un
   * token sin los claims esperados).
   *
   * @param ex excepcion de autenticacion
   * @param request peticion en curso
   * @return problema 401
   */
  @ExceptionHandler(AuthenticationException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ProblemDetail handleNoAutenticado(
      AuthenticationException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "Token no valido",
        "Se requiere un access token valido emitido por el IDaaS configurado.",
        request);
  }

  @ExceptionHandler(RecursoNoEncontradoException.class)
  public ProblemDetail handleNoEncontrado(
      RecursoNoEncontradoException ex, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
  }

  @ExceptionHandler(ReglaNegocioException.class)
  public ProblemDetail handleReglaNegocio(ReglaNegocioException ex, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, "Regla de negocio no cumplida", ex.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidacion(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> errores = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));

    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            "Datos invalidos",
            "Revise los campos observados en la propiedad 'errores'.",
            request);
    problem.setProperty("errores", errores);
    return problem;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleCuerpoIlegible(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Cuerpo de la peticion invalido",
        "No fue posible interpretar el JSON enviado.",
        request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleArgumentoIlegal(
      IllegalArgumentException ex, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "Peticion invalida", ex.getMessage(), request);
  }

  @ExceptionHandler(RestClientException.class)
  public ProblemDetail handleDownstream(RestClientException ex, HttpServletRequest request) {
    log.error("Fallo al invocar un microservicio aguas abajo", ex);
    return problem(
        HttpStatus.BAD_GATEWAY,
        "Servicio no disponible",
        "Un microservicio dependiente no respondio correctamente.",
        request);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleInesperado(Exception ex, HttpServletRequest request) {
    log.error("Error inesperado procesando {} {}", request.getMethod(), request.getRequestURI(), ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Error interno",
        "Ocurrio un error inesperado. Revise los logs del servicio.",
        request);
  }

  private ProblemDetail problem(
      HttpStatus status, String titulo, String detalle, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(titulo);
    problem.setDetail(detalle);
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("timestamp", OffsetDateTime.now().toString());
    return problem;
  }
}
