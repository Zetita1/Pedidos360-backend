package cl.duoc.pedidos360.notificaciones.repository;

import cl.duoc.pedidos360.notificaciones.domain.Documento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los tickets generados por los consumidores. */
public interface DocumentoRepository extends JpaRepository<Documento, Long> {

  /**
   * Indica si el evento ya genero su documento, para no emitirlo dos veces.
   *
   * @param eventoId identificador del evento de origen
   * @return true si ya existe
   */
  boolean existsByEventoId(String eventoId);

  /**
   * Ultimos documentos emitidos.
   *
   * @param limite cantidad maxima a devolver
   * @return documentos del mas reciente al mas antiguo
   */
  List<Documento> findAllByOrderByCreadoEnDesc(Limit limite);

  /**
   * Documento de un pedido.
   *
   * @param pedidoNumero correlativo del pedido
   * @return documento, si existe
   */
  Optional<Documento> findByPedidoNumero(String pedidoNumero);

  /**
   * Cuenta los documentos emitidos en el ano indicado, para generar el folio.
   *
   * @param prefijo prefijo del folio, por ejemplo {@code TCK-2026-}
   * @return cantidad de documentos con ese prefijo
   */
  long countByFolioStartingWith(String prefijo);
}
