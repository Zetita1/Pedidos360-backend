package cl.duoc.pedidos360.clientes.repository;

import cl.duoc.pedidos360.clientes.domain.Cliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Acceso a datos de los clientes. */
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

  Optional<Cliente> findByRut(String rut);

  Optional<Cliente> findByUsuarioOid(String usuarioOid);

  boolean existsByRut(String rut);

  /**
   * Busca clientes por rut, razon social o correo.
   *
   * @param texto fragmento de busqueda, puede ser {@code null}
   * @param soloActivos {@code true} para excluir fichas dadas de baja
   * @return clientes que cumplen el filtro
   */
  @Query(
      """
      select c from Cliente c
      where (:texto is null
             or lower(c.razonSocial) like lower(concat('%', :texto, '%'))
             or lower(c.rut) like lower(concat('%', :texto, '%'))
             or lower(c.email) like lower(concat('%', :texto, '%')))
        and (:soloActivos = false or c.activo = true)
      order by c.razonSocial asc
      """)
  List<Cliente> buscar(@Param("texto") String texto, @Param("soloActivos") boolean soloActivos);
}
