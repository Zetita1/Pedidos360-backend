package cl.duoc.pedidos360.notificaciones.web;

import cl.duoc.pedidos360.notificaciones.service.AuditoriaService;
import cl.duoc.pedidos360.notificaciones.service.DocumentoService;
import cl.duoc.pedidos360.notificaciones.service.NotificacionService;
import cl.duoc.pedidos360.notificaciones.web.NotificacionDtos.AuditoriaResponse;
import cl.duoc.pedidos360.notificaciones.web.NotificacionDtos.DocumentoResponse;
import cl.duoc.pedidos360.notificaciones.web.NotificacionDtos.NotificacionResponse;
import cl.duoc.pedidos360.security.SecurityRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de consulta de lo que produjeron los consumidores: avisos y tickets.
 *
 * <p>Es solo lectura: nada se crea por HTTP, todo entra por las colas.
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
@Tag(name = "Notificaciones", description = "Avisos y documentos generados desde RabbitMQ")
@SecurityRequirement(name = "entra-id")
@Validated
public class NotificacionController {

  private final NotificacionService notificaciones;
  private final DocumentoService documentos;
  private final AuditoriaService auditoria;

  public NotificacionController(
      NotificacionService notificaciones,
      DocumentoService documentos,
      AuditoriaService auditoria) {
    this.notificaciones = notificaciones;
    this.documentos = documentos;
    this.auditoria = auditoria;
  }

  @GetMapping
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Ultimas notificaciones generadas por los consumidores")
  public List<NotificacionResponse> listar(
      @RequestParam(defaultValue = "50") @Positive @Max(200) int limite) {
    return notificaciones.ultimas(limite).stream().map(NotificacionResponse::de).toList();
  }

  @GetMapping("/pedido/{numero}")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Notificaciones de un pedido")
  public List<NotificacionResponse> porPedido(@PathVariable String numero) {
    return notificaciones.porPedido(numero).stream().map(NotificacionResponse::de).toList();
  }

  @GetMapping("/documentos")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Ultimos tickets emitidos")
  public List<DocumentoResponse> documentos(
      @RequestParam(defaultValue = "20") @Positive @Max(100) int limite) {
    return documentos.ultimos(limite).stream().map(DocumentoResponse::de).toList();
  }

  @GetMapping("/documentos/pedido/{numero}")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Ticket de un pedido confirmado")
  public DocumentoResponse documentoDePedido(@PathVariable String numero) {
    return DocumentoResponse.de(documentos.porPedido(numero));
  }

  @GetMapping("/auditoria")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Operaciones sensibles registradas por el consumidor de auditoria")
  public List<AuditoriaResponse> auditoria(
      @RequestParam(defaultValue = "50") @Positive @Max(200) int limite) {
    return auditoria.ultimos(limite).stream().map(AuditoriaResponse::de).toList();
  }

  @GetMapping("/auditoria/pedido/{numero}")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Auditoria de un pedido")
  public List<AuditoriaResponse> auditoriaDePedido(@PathVariable String numero) {
    return auditoria.porPedido(numero).stream().map(AuditoriaResponse::de).toList();
  }
}
