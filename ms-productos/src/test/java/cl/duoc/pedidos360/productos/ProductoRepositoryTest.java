package cl.duoc.pedidos360.productos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.duoc.pedidos360.productos.domain.Producto;
import cl.duoc.pedidos360.productos.repository.ProductoRepository;
import cl.duoc.pedidos360.web.ReglaNegocioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/** Pruebas de la capa de persistencia del catalogo contra la base en memoria. */
@DataJpaTest
class ProductoRepositoryTest {

  @Autowired private ProductoRepository repository;

  @BeforeEach
  void cargarCatalogo() {
    // deleteAllInBatch emite el DELETE de inmediato; con deleteAll() Hibernate ordenaria
    // los INSERT antes que los DELETE y chocaria con los datos de ejemplo del perfil local.
    repository.deleteAllInBatch();
    repository.save(
        new Producto("NB-001", "Notebook Pro 14", "16GB RAM", new BigDecimal("899990"), 10, "Computacion"));
    repository.save(
        new Producto("TC-003", "Teclado mecanico", "Switches rojos", new BigDecimal("59990"), 50, "Accesorios"));
    Producto descontinuado =
        new Producto("OLD-9", "Impresora matriz", "Modelo antiguo", new BigDecimal("49990"), 2, "Oficina");
    descontinuado.desactivar();
    repository.save(descontinuado);
  }

  @Test
  @DisplayName("Persiste el producto y genera id, timestamps y version")
  void persisteProducto() {
    Producto guardado = repository.findBySkuIgnoreCase("nb-001").orElseThrow();

    assertThat(guardado.getId()).isNotNull();
    assertThat(guardado.getCreadoEn()).isNotNull();
    assertThat(guardado.getActualizadoEn()).isNotNull();
    assertThat(guardado.getVersion()).isNotNull();
    assertThat(guardado.isActivo()).isTrue();
  }

  @Test
  @DisplayName("Filtra por texto libre y excluye los productos dados de baja")
  void filtraCatalogo() {
    assertThat(repository.buscar("teclado", null, true)).hasSize(1);
    assertThat(repository.buscar(null, "Computacion", true)).hasSize(1);
    assertThat(repository.buscar(null, null, true)).hasSize(2);
    assertThat(repository.buscar(null, null, false)).hasSize(3);
  }

  @Test
  @DisplayName("Descuenta stock y rechaza cuando no alcanza")
  void controlaStock() {
    Producto producto = repository.findBySkuIgnoreCase("NB-001").orElseThrow();

    producto.descontarStock(4);
    assertThat(producto.getStock()).isEqualTo(6);

    assertThatThrownBy(() -> producto.descontarStock(99))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("Stock insuficiente");
  }

  @Test
  @DisplayName("No permite vender un producto dado de baja")
  void rechazaProductoInactivo() {
    Producto inactivo = repository.findBySkuIgnoreCase("OLD-9").orElseThrow();

    assertThatThrownBy(() -> inactivo.descontarStock(1))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("no esta disponible");
  }

  @Test
  @DisplayName("Lista las categorias del catalogo")
  void listaCategorias() {
    assertThat(repository.listarCategorias())
        .containsExactly("Accesorios", "Computacion", "Oficina");
  }
}
