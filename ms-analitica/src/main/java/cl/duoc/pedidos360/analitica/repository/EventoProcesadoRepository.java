package cl.duoc.pedidos360.analitica.repository;

import cl.duoc.pedidos360.analitica.domain.EventoProcesado;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al registro de eventos ya procesados. */
public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, Long> {

  /**
   * Si un evento ya fue procesado.
   *
   * @param eventoId identificador del evento
   * @return true cuando el evento ya esta registrado
   */
  boolean existsByEventoId(String eventoId);

  /**
   * Ultimos eventos procesados, para seguir el flujo en vivo.
   *
   * @param limite cuantos devolver
   * @return eventos mas recientes primero
   */
  List<EventoProcesado> findAllByOrderByProcesadoEnDesc(Pageable limite);

  /**
   * Cuantos eventos se procesaron desde un topico.
   *
   * @param topico nombre del topico
   * @return total de eventos procesados de ese topico
   */
  long countByTopico(String topico);
}
