package cl.duoc.pedidos360.notificaciones.web;

import cl.duoc.pedidos360.notificaciones.domain.Documento;
import cl.duoc.pedidos360.notificaciones.domain.Notificacion;
import cl.duoc.pedidos360.notificaciones.domain.TipoNotificacion;
import java.math.BigDecimal;
import java.time.Instant;

/** Contratos de salida del API de notificaciones y documentos. */
public final class NotificacionDtos {

  private NotificacionDtos() {}

  /**
   * Aviso generado por un consumidor.
   *
   * @param id identificador del aviso
   * @param tipo motivo del aviso
   * @param pedidoNumero correlativo del pedido
   * @param clienteId cliente destinatario
   * @param destinatario correo de contacto
   * @param asunto titulo del aviso
   * @param mensaje cuerpo del aviso
   * @param eventoId evento de RabbitMQ que lo origino
   * @param creadoEn momento en que se genero
   */
  public record NotificacionResponse(
      Long id,
      TipoNotificacion tipo,
      String pedidoNumero,
      Long clienteId,
      String destinatario,
      String asunto,
      String mensaje,
      String eventoId,
      Instant creadoEn) {

    /**
     * Construye la respuesta desde la entidad.
     *
     * @param notificacion entidad persistida
     * @return representacion publica
     */
    public static NotificacionResponse de(Notificacion notificacion) {
      return new NotificacionResponse(
          notificacion.getId(),
          notificacion.getTipo(),
          notificacion.getPedidoNumero(),
          notificacion.getClienteId(),
          notificacion.getDestinatario(),
          notificacion.getAsunto(),
          notificacion.getMensaje(),
          notificacion.getEventoId(),
          notificacion.getCreadoEn());
    }
  }

  /**
   * Ticket emitido para un pedido confirmado.
   *
   * @param id identificador del documento
   * @param folio correlativo del ticket
   * @param pedidoNumero correlativo del pedido
   * @param clienteId cliente dueno del pedido
   * @param clienteNombre nombre del cliente
   * @param total monto total
   * @param contenido ticket en texto plano
   * @param eventoId evento de RabbitMQ que lo origino
   * @param creadoEn momento de emision
   */
  public record DocumentoResponse(
      Long id,
      String folio,
      String pedidoNumero,
      Long clienteId,
      String clienteNombre,
      BigDecimal total,
      String contenido,
      String eventoId,
      Instant creadoEn) {

    /**
     * Construye la respuesta desde la entidad.
     *
     * @param documento entidad persistida
     * @return representacion publica
     */
    public static DocumentoResponse de(Documento documento) {
      return new DocumentoResponse(
          documento.getId(),
          documento.getFolio(),
          documento.getPedidoNumero(),
          documento.getClienteId(),
          documento.getClienteNombre(),
          documento.getTotal(),
          documento.getContenido(),
          documento.getEventoId(),
          documento.getCreadoEn());
    }
  }
}
