package cl.duoc.pedidos360.productos.repository;

import cl.duoc.pedidos360.productos.domain.Producto;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Acceso a datos del catalogo de productos (PostgreSQL en la nube, H2 en desarrollo). */
@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

  Optional<Producto> findBySkuIgnoreCase(String sku);

  boolean existsBySkuIgnoreCase(String sku);

  List<Producto> findByActivoTrueOrderByNombreAsc();

  /**
   * Busqueda del catalogo por texto libre y categoria, ambos opcionales.
   *
   * @param texto fragmento de nombre o sku, puede ser {@code null}
   * @param categoria categoria exacta, puede ser {@code null}
   * @param soloActivos {@code true} para excluir productos dados de baja
   * @return productos que cumplen los filtros
   */
  @Query(
      """
      select p from Producto p
      where (:texto is null
             or lower(p.nombre) like lower(concat('%', :texto, '%'))
             or lower(p.sku) like lower(concat('%', :texto, '%')))
        and (:categoria is null or lower(p.categoria) = lower(:categoria))
        and (:soloActivos = false or p.activo = true)
      order by p.nombre asc
      """)
  List<Producto> buscar(
      @Param("texto") String texto,
      @Param("categoria") String categoria,
      @Param("soloActivos") boolean soloActivos);

  /**
   * Carga el producto bloqueando la fila para operar el stock sin condiciones de carrera.
   *
   * @param id identificador del producto
   * @return producto bloqueado
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Producto p where p.id = :id")
  Optional<Producto> findByIdParaActualizarStock(@Param("id") Long id);

  @Query("select distinct p.categoria from Producto p order by p.categoria asc")
  List<String> listarCategorias();
}
