package cl.duoc.pedidos360.analitica.repository;

import cl.duoc.pedidos360.analitica.domain.MetricaDiaria;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las metricas diarias proyectadas desde el topico de pedidos. */
public interface MetricaDiariaRepository extends JpaRepository<MetricaDiaria, Long> {

  /**
   * Metrica de un dia.
   *
   * @param fecha dia buscado
   * @return metrica del dia, vacio si aun no tiene hechos
   */
  Optional<MetricaDiaria> findByFecha(LocalDate fecha);

  /**
   * Metricas desde una fecha, de la mas reciente a la mas antigua.
   *
   * @param desde primer dia incluido
   * @return metricas del periodo
   */
  List<MetricaDiaria> findByFechaGreaterThanEqualOrderByFechaDesc(LocalDate desde);
}
