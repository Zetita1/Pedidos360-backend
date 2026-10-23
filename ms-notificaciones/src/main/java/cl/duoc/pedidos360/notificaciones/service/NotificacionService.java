package cl.duoc.pedidos360.notificaciones.service;

import cl.duoc.pedidos360.mensajeria.consumo.EventoNoProcesableException;
import cl.duoc.pedidos360.mensajeria.evento.PedidoCreadoEvento;
import cl.duoc.pedidos360.mensajeria.evento.PedidoEstadoCambiadoEvento;
import cl.duoc.pedidos360.notificaciones.domain.Notificacion;
import cl.duoc.pedidos360.notificaciones.domain.TipoNotificacion;
import cl.duoc.pedidos360.notificaciones.repository.NotificacionRepository;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera los avisos al cliente a partir de los eventos de pedidos.
 *
 * <p>Es logica de negocio pura: no conoce colas, exchanges ni canales AMQP. Quien la invoca
 * es el consumidor, que ademas resuelve la confirmacion del mensaje.
 */
@Service
public class NotificacionService {

  private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

  private final NotificacionRepository repository;

  public NotificacionService(NotificacionRepository repository) {
    this.repository = repository;
  }

  /**
   * Registra el aviso de recepcion de un pedido nuevo.
   *
   * @param evento evento de pedido creado
   */
  @Transactional
  public void registrarRecepcion(PedidoCreadoEvento evento) {
    exigirPedido(evento.pedidoNumero(), evento.eventoId());
    if (yaProcesado(evento.eventoId(), evento.pedidoNumero())) {
      return;
    }

    String mensaje =
        "Recibimos tu pedido %s con %d producto(s) por un total de %s. Te avisaremos cuando quede confirmado."
            .formatted(evento.pedidoNumero(), evento.lineas(), montoLegible(evento.total()));

    guardar(
        new Notificacion(
            evento.eventoId(),
            TipoNotificacion.RECEPCION,
            evento.pedidoNumero(),
            evento.pedidoId(),
            evento.clienteId(),
            evento.clienteEmail(),
            "Pedido " + evento.pedidoNumero() + " recibido",
            mensaje));
  }

  /**
   * Registra el aviso de seguimiento cuando el pedido cambia de estado.
   *
   * @param evento evento de cambio de estado
   */
  @Transactional
  public void registrarSeguimiento(PedidoEstadoCambiadoEvento evento) {
    exigirPedido(evento.pedidoNumero(), evento.eventoId());
    if (evento.estadoNuevo() == null || evento.estadoNuevo().isBlank()) {
      throw new EventoNoProcesableException(
          "El evento " + evento.eventoId() + " no indica el estado nuevo del pedido");
    }
    if (yaProcesado(evento.eventoId(), evento.pedidoNumero())) {
      return;
    }

    TipoNotificacion tipo =
        "CONFIRMADO".equalsIgnoreCase(evento.estadoNuevo())
            ? TipoNotificacion.CONFIRMACION
            : TipoNotificacion.SEGUIMIENTO;

    StringBuilder mensaje =
        new StringBuilder(
            "Tu pedido %s paso de %s a %s."
                .formatted(
                    evento.pedidoNumero(),
                    legible(evento.estadoAnterior()),
                    legible(evento.estadoNuevo())));
    if (evento.motivo() != null && !evento.motivo().isBlank()) {
      mensaje.append(" Motivo: ").append(evento.motivo()).append('.');
    }

    guardar(
        new Notificacion(
            evento.eventoId(),
            tipo,
            evento.pedidoNumero(),
            evento.pedidoId(),
            evento.clienteId(),
            evento.clienteEmail(),
            "Pedido " + evento.pedidoNumero() + ": " + legible(evento.estadoNuevo()),
            mensaje.toString()));
  }

  /**
   * Ultimas notificaciones generadas.
   *
   * @param limite cantidad maxima a devolver
   * @return notificaciones de la mas reciente a la mas antigua
   */
  @Transactional(readOnly = true)
  public List<Notificacion> ultimas(int limite) {
    return repository.findAllByOrderByCreadoEnDesc(Limit.of(limite));
  }

  /**
   * Notificaciones de un pedido.
   *
   * @param pedidoNumero correlativo del pedido
   * @return notificaciones del pedido
   */
  @Transactional(readOnly = true)
  public List<Notificacion> porPedido(String pedidoNumero) {
    return repository.findByPedidoNumeroOrderByCreadoEnDesc(pedidoNumero);
  }

  /**
   * Valida que el evento traiga el pedido al que se refiere.
   *
   * @param pedidoNumero correlativo recibido
   * @param eventoId identificador del evento
   */
  private void exigirPedido(String pedidoNumero, String eventoId) {
    if (pedidoNumero == null || pedidoNumero.isBlank()) {
      throw new EventoNoProcesableException(
          "El evento " + eventoId + " llego sin numero de pedido");
    }
  }

  /**
   * Comprueba si el evento ya fue procesado antes.
   *
   * @param eventoId identificador del evento
   * @param pedidoNumero correlativo del pedido, solo para el log
   * @return true si ya existe una notificacion para ese evento
   */
  private boolean yaProcesado(String eventoId, String pedidoNumero) {
    if (repository.existsByEventoId(eventoId)) {
      log.info(
          "El evento {} del pedido {} ya tenia notificacion; se omite el duplicado",
          eventoId,
          pedidoNumero);
      return true;
    }
    return false;
  }

  private void guardar(Notificacion notificacion) {
    repository.save(notificacion);
    log.info(
        "Notificacion {} generada para el pedido {}",
        notificacion.getTipo(),
        notificacion.getPedidoNumero());
  }

  private String montoLegible(java.math.BigDecimal total) {
    if (total == null) {
      return "monto por confirmar";
    }
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CL")).format(total);
  }

  private String legible(String estado) {
    if (estado == null || estado.isBlank()) {
      return "sin estado";
    }
    return estado.toLowerCase(Locale.ROOT).replace('_', ' ');
  }
}
