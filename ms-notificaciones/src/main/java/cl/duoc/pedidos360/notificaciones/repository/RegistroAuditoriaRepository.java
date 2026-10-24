package cl.duoc.pedidos360.notificaciones.repository;

import cl.duoc.pedidos360.notificaciones.domain.RegistroAuditoria;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al registro de operaciones sensibles. */
public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

  /**
   * Indica si el comando ya fue registrado, para no duplicarlo ante un reintento.
   *
   * @param eventoId identificador del comando de origen
   * @return true si ya existe
   */
  boolean existsByEventoId(String eventoId);

  /**
   * Ultimos registros de auditoria.
   *
   * @param limite cantidad maxima a devolver
   * @return registros del mas reciente al mas antiguo
   */
  List<RegistroAuditoria> findAllByOrderByOcurridoEnDesc(Limit limite);

  /**
   * Registros de un pedido.
   *
   * @param pedidoNumero correlativo del pedido
   * @return registros del mas reciente al mas antiguo
   */
  List<RegistroAuditoria> findByPedidoNumeroOrderByOcurridoEnDesc(String pedidoNumero);
}
