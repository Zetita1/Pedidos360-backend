package cl.duoc.pedidos360.notificaciones.repository;

import cl.duoc.pedidos360.notificaciones.domain.Notificacion;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los avisos generados por los consumidores. */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

  /**
   * Indica si el evento ya genero una notificacion, para no duplicarla ante un reintento.
   *
   * @param eventoId identificador del evento de origen
   * @return true si ya existe
   */
  boolean existsByEventoId(String eventoId);

  /**
   * Ultimas notificaciones registradas.
   *
   * @param limite cantidad maxima a devolver
   * @return notificaciones de la mas reciente a la mas antigua
   */
  List<Notificacion> findAllByOrderByCreadoEnDesc(Limit limite);

  /**
   * Notificaciones de un pedido.
   *
   * @param pedidoNumero correlativo del pedido
   * @return notificaciones de la mas reciente a la mas antigua
   */
  List<Notificacion> findByPedidoNumeroOrderByCreadoEnDesc(String pedidoNumero);
}
