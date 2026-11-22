package cl.duoc.pedidos360.analitica.repository;

import cl.duoc.pedidos360.analitica.domain.EstadoStock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a la proyeccion de stock construida desde el topico compactado. */
public interface EstadoStockRepository extends JpaRepository<EstadoStock, Long> {

  /**
   * Productos en o bajo el umbral, primero los mas escasos.
   *
   * @param umbral unidades a partir de las cuales se considera critico
   * @return productos criticos
   */
  List<EstadoStock> findByStockLessThanEqualOrderByStockAsc(Integer umbral);

  /**
   * Proyeccion completa, de la actualizacion mas reciente a la mas antigua.
   *
   * @return estado de todos los productos conocidos
   */
  List<EstadoStock> findAllByOrderByActualizadoEnDesc();

  /**
   * Valor total del inventario proyectado.
   *
   * @return suma de precio por unidades, cero si no hay datos
   */
  @Query("select coalesce(sum(e.precio * e.stock), 0) from EstadoStock e")
  java.math.BigDecimal valorizado();

  /**
   * Cuantos productos estan en o bajo el umbral.
   *
   * @param umbral unidades a partir de las cuales se considera critico
   * @return cantidad de productos criticos
   */
  @Query("select count(e) from EstadoStock e where e.stock <= :umbral")
  long contarCriticos(@Param("umbral") Integer umbral);
}
