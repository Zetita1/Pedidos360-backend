package cl.duoc.pedidos360.productos.service;

import cl.duoc.pedidos360.productos.domain.Producto;
import cl.duoc.pedidos360.productos.repository.ProductoRepository;
import cl.duoc.pedidos360.productos.web.ProductoDtos.ActualizarProductoRequest;
import cl.duoc.pedidos360.productos.web.ProductoDtos.CrearProductoRequest;
import cl.duoc.pedidos360.web.RecursoNoEncontradoException;
import cl.duoc.pedidos360.web.ReglaNegocioException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reglas de negocio del catalogo de productos. */
@Service
public class ProductoService {

  private static final Logger log = LoggerFactory.getLogger(ProductoService.class);

  private final ProductoRepository repository;

  public ProductoService(ProductoRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<Producto> buscar(String texto, String categoria, boolean soloActivos) {
    String textoNormalizado = (texto == null || texto.isBlank()) ? null : texto.trim();
    String categoriaNormalizada = (categoria == null || categoria.isBlank()) ? null : categoria.trim();
    return repository.buscar(textoNormalizado, categoriaNormalizada, soloActivos);
  }

  @Transactional(readOnly = true)
  public Producto obtener(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new RecursoNoEncontradoException("producto", id));
  }

  @Transactional(readOnly = true)
  public Producto obtenerPorSku(String sku) {
    return repository
        .findBySkuIgnoreCase(sku)
        .orElseThrow(() -> new RecursoNoEncontradoException("No existe producto con sku " + sku));
  }

  @Transactional(readOnly = true)
  public List<String> categorias() {
    return repository.listarCategorias();
  }

  @Transactional
  public Producto crear(CrearProductoRequest request) {
    if (repository.existsBySkuIgnoreCase(request.sku())) {
      throw new ReglaNegocioException("Ya existe un producto con el sku " + request.sku());
    }
    Producto producto =
        new Producto(
            request.sku().trim().toUpperCase(),
            request.nombre().trim(),
            request.descripcion(),
            request.precio(),
            request.stock(),
            request.categoria().trim());
    Producto guardado = repository.save(producto);
    log.info("Producto creado id={} sku={}", guardado.getId(), guardado.getSku());
    return guardado;
  }

  @Transactional
  public Producto actualizar(Long id, ActualizarProductoRequest request) {
    Producto producto = obtener(id);
    producto.actualizarDatos(
        request.nombre().trim(),
        request.descripcion(),
        request.precio(),
        request.stock(),
        request.categoria().trim());
    return producto;
  }

  @Transactional
  public void desactivar(Long id) {
    obtener(id).desactivar();
    log.info("Producto id={} desactivado", id);
  }

  @Transactional
  public Producto descontarStock(Long id, int cantidad, String motivo) {
    Producto producto =
        repository
            .findByIdParaActualizarStock(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("producto", id));
    producto.descontarStock(cantidad);
    log.info("Stock descontado producto={} cantidad={} motivo={}", id, cantidad, motivo);
    return producto;
  }

  @Transactional
  public Producto reponerStock(Long id, int cantidad, String motivo) {
    Producto producto =
        repository
            .findByIdParaActualizarStock(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("producto", id));
    producto.reponerStock(cantidad);
    log.info("Stock repuesto producto={} cantidad={} motivo={}", id, cantidad, motivo);
    return producto;
  }
}
