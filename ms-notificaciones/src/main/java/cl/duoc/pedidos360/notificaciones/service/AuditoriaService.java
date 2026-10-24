package cl.duoc.pedidos360.notificaciones.service;

import cl.duoc.pedidos360.mensajeria.consumo.EventoNoProcesableException;
import cl.duoc.pedidos360.mensajeria.evento.PedidoAnuladoEvento;
import cl.duoc.pedidos360.notificaciones.domain.RegistroAuditoria;
import cl.duoc.pedidos360.notificaciones.repository.RegistroAuditoriaRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deja constancia de las operaciones sensibles sobre pedidos.
 *
 * <p>Una anulacion mueve dinero y stock: aunque el aviso al cliente falle, el registro de
 * quien la hizo y por que tiene que quedar. Por eso vive en su propia cola, con su propia
 * DLQ, y no compite con las notificaciones.
 */
@Service
public class AuditoriaService {

  private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

  /** Operacion que se audita hoy; la entidad admite otras sin cambiar el esquema. */
  private static final String OPERACION_ANULACION = "PEDIDO_ANULADO";

  private final RegistroAuditoriaRepository repository;

  public AuditoriaService(RegistroAuditoriaRepository repository) {
    this.repository = repository;
  }

  /**
   * Registra la anulacion de un pedido.
   *
   * @param evento comando de pedido anulado
   */
  @Transactional
  public void registrarAnulacion(PedidoAnuladoEvento evento) {
    if (evento.pedidoNumero() == null || evento.pedidoNumero().isBlank()) {
      throw new EventoNoProcesableException(
          "El comando " + evento.eventoId() + " llego sin numero de pedido");
    }
    if (evento.usuarioOid() == null || evento.usuarioOid().isBlank()) {
      throw new EventoNoProcesableException(
          "La anulacion del pedido "
              + evento.pedidoNumero()
              + " llego sin responsable: un registro de auditoria sin autor no sirve");
    }
    if (repository.existsByEventoId(evento.eventoId())) {
      log.info(
          "El comando {} del pedido {} ya estaba auditado; se omite el duplicado",
          evento.eventoId(),
          evento.pedidoNumero());
      return;
    }

    repository.save(
        new RegistroAuditoria(
            evento.eventoId(),
            OPERACION_ANULACION,
            evento.pedidoNumero(),
            evento.clienteId(),
            evento.usuarioOid(),
            evento.usuarioNombre(),
            evento.estadoAnterior(),
            evento.total(),
            evento.motivo(),
            evento.ocurridoEn()));

    log.info(
        "Auditoria: pedido {} anulado por {} (estado previo {})",
        evento.pedidoNumero(),
        evento.usuarioNombre() == null ? evento.usuarioOid() : evento.usuarioNombre(),
        evento.estadoAnterior());
  }

  /**
   * Ultimos registros de auditoria.
   *
   * @param limite cantidad maxima a devolver
   * @return registros del mas reciente al mas antiguo
   */
  @Transactional(readOnly = true)
  public List<RegistroAuditoria> ultimos(int limite) {
    return repository.findAllByOrderByOcurridoEnDesc(Limit.of(limite));
  }

  /**
   * Registros de un pedido.
   *
   * @param pedidoNumero correlativo del pedido
   * @return registros del pedido
   */
  @Transactional(readOnly = true)
  public List<RegistroAuditoria> porPedido(String pedidoNumero) {
    return repository.findByPedidoNumeroOrderByOcurridoEnDesc(pedidoNumero);
  }
}
