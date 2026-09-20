package cl.duoc.pedidos360.pedidos.repository;

import cl.duoc.pedidos360.pedidos.domain.EstadoPedido;
import cl.duoc.pedidos360.pedidos.domain.Pedido;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Acceso a datos de los pedidos. */
@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

  Optional<Pedido> findByNumero(String numero);

  List<Pedido> findByClienteIdOrderByCreadoEnDesc(Long clienteId);

  List<Pedido> findByUsuarioOidOrderByCreadoEnDesc(String usuarioOid);

  /**
   * Busqueda de pedidos por estado y/o cliente, ambos opcionales.
   *
   * @param estado estado exacto, puede ser {@code null}
   * @param clienteId cliente duenio del pedido, puede ser {@code null}
   * @return pedidos ordenados del mas reciente al mas antiguo
   */
  @Query(
      """
      select distinct p from Pedido p
      left join fetch p.detalles
      where (:estado is null or p.estado = :estado)
        and (:clienteId is null or p.clienteId = :clienteId)
      order by p.creadoEn desc
      """)
  List<Pedido> buscar(
      @Param("estado") EstadoPedido estado, @Param("clienteId") Long clienteId);

  @Query("select count(p) from Pedido p where p.estado = :estado")
  long contarPorEstado(@Param("estado") EstadoPedido estado);

  /**
   * Correlativo usado para construir el numero de pedido del anio en curso.
   *
   * @param prefijo prefijo del numero, por ejemplo {@code PED-2026-}
   * @return cantidad de pedidos ya emitidos con ese prefijo
   */
  @Query("select count(p) from Pedido p where p.numero like concat(:prefijo, '%')")
  long contarPorPrefijo(@Param("prefijo") String prefijo);
}
