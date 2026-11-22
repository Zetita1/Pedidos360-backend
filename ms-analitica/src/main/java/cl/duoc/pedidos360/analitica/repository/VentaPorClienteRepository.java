package cl.duoc.pedidos360.analitica.repository;

import cl.duoc.pedidos360.analitica.domain.VentaPorCliente;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al acumulado de ventas por cliente. */
public interface VentaPorClienteRepository extends JpaRepository<VentaPorCliente, Long> {

  /**
   * Clientes ordenados por monto comprado.
   *
   * @param limite cuantos devolver
   * @return ranking de clientes
   */
  List<VentaPorCliente> findAllByOrderByMontoTotalDesc(Pageable limite);
}
