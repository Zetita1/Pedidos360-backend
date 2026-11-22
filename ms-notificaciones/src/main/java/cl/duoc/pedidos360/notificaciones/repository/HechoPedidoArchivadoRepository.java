package cl.duoc.pedidos360.notificaciones.repository;

import cl.duoc.pedidos360.notificaciones.domain.HechoPedidoArchivado;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al archivo de hechos de pedidos leidos desde Kafka. */
public interface HechoPedidoArchivadoRepository extends JpaRepository<HechoPedidoArchivado, Long> {

  /**
   * Si el hecho ya esta archivado.
   *
   * @param eventoId identificador del evento
   * @return true cuando ya se archivo
   */
  boolean existsByEventoId(String eventoId);

  /**
   * Ultimos hechos archivados.
   *
   * @param limite cuantos devolver
   * @return hechos mas recientes primero
   */
  List<HechoPedidoArchivado> findAllByOrderByArchivadoEnDesc(Pageable limite);

  /**
   * Historia completa de un pedido, en el orden en que ocurrio.
   *
   * @param pedidoNumero correlativo del pedido
   * @return hechos del pedido del mas antiguo al mas reciente
   */
  List<HechoPedidoArchivado> findByPedidoNumeroOrderByOcurridoEnAsc(String pedidoNumero);
}
