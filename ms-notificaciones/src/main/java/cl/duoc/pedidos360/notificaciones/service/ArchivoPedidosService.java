package cl.duoc.pedidos360.notificaciones.service;

import cl.duoc.pedidos360.notificaciones.domain.HechoPedidoArchivado;
import cl.duoc.pedidos360.notificaciones.repository.HechoPedidoArchivadoRepository;
import cl.duoc.pedidos360.streaming.evento.PedidoStreamEvento;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda cada hecho de pedido tal como llego por el topico.
 *
 * <p>Como el archivo no resume nada, la unica regla que aplica es la idempotencia: el mismo
 * registro puede llegar dos veces y no debe quedar archivado dos veces.
 */
@Service
public class ArchivoPedidosService {

  private static final Logger log = LoggerFactory.getLogger(ArchivoPedidosService.class);

  private final HechoPedidoArchivadoRepository archivo;

  public ArchivoPedidosService(HechoPedidoArchivadoRepository archivo) {
    this.archivo = archivo;
  }

  /**
   * Archiva un hecho de pedido.
   *
   * @param evento hecho recibido
   * @param topico topico de origen
   * @param particion particion de origen
   * @param offset posicion en la particion
   * @return true si se archivo, false si ya estaba
   */
  @Transactional
  public boolean archivar(PedidoStreamEvento evento, String topico, int particion, long offset) {
    if (archivo.existsByEventoId(evento.eventoId())) {
      log.debug("Hecho {} ya estaba archivado, se descarta", evento.eventoId());
      return false;
    }
    archivo.save(
        new HechoPedidoArchivado(
            evento.eventoId(),
            evento.tipo().name(),
            evento.pedidoNumero(),
            evento.clienteId(),
            evento.clienteNombre(),
            evento.total(),
            evento.estadoAnterior(),
            evento.estadoNuevo(),
            evento.motivo(),
            evento.usuarioOid(),
            topico,
            particion,
            offset,
            evento.ocurridoEn()));
    log.info(
        "Hecho {} del pedido {} archivado desde {}-{} offset {}",
        evento.tipo(),
        evento.pedidoNumero(),
        topico,
        particion,
        offset);
    return true;
  }

  /**
   * Ultimos hechos archivados.
   *
   * @param limite cuantos devolver
   * @return hechos mas recientes primero
   */
  @Transactional(readOnly = true)
  public List<HechoPedidoArchivado> ultimos(int limite) {
    return archivo.findAllByOrderByArchivadoEnDesc(PageRequest.of(0, Math.max(limite, 1)));
  }

  /**
   * Historia de un pedido.
   *
   * @param pedidoNumero correlativo del pedido
   * @return hechos del pedido en orden cronologico
   */
  @Transactional(readOnly = true)
  public List<HechoPedidoArchivado> historiaDe(String pedidoNumero) {
    return archivo.findByPedidoNumeroOrderByOcurridoEnAsc(pedidoNumero);
  }
}
