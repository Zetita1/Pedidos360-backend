package cl.duoc.pedidos360.analitica.repository;

import cl.duoc.pedidos360.analitica.domain.AlertaStock;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los avisos de stock generados por el consumidor. */
public interface AlertaStockRepository extends JpaRepository<AlertaStock, Long> {

  /**
   * Ultimos avisos generados.
   *
   * @param limite cuantos devolver
   * @return avisos mas recientes primero
   */
  List<AlertaStock> findAllByOrderByGeneradaEnDesc(Pageable limite);
}
