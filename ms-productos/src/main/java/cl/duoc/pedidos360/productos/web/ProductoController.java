package cl.duoc.pedidos360.productos.web;

import cl.duoc.pedidos360.productos.service.ProductoService;
import cl.duoc.pedidos360.productos.web.ProductoDtos.ActualizarProductoRequest;
import cl.duoc.pedidos360.productos.web.ProductoDtos.CrearProductoRequest;
import cl.duoc.pedidos360.productos.web.ProductoDtos.MovimientoStockRequest;
import cl.duoc.pedidos360.productos.web.ProductoDtos.ProductoResponse;
import cl.duoc.pedidos360.security.SecurityRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API REST del catalogo. Todos los endpoints exigen un JWT valido del IDaaS. */
@RestController
@RequestMapping("/api/v1/productos")
@Tag(name = "Productos", description = "Catalogo de productos de Pedidos360")
@SecurityRequirement(name = "entra-id")
public class ProductoController {

  private final ProductoService service;

  public ProductoController(ProductoService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Lista el catalogo con filtros opcionales de texto y categoria")
  public List<ProductoResponse> listar(
      @RequestParam(required = false) String buscar,
      @RequestParam(required = false) String categoria,
      @RequestParam(defaultValue = "true") boolean soloActivos) {
    return service.buscar(buscar, categoria, soloActivos).stream().map(ProductoResponse::de).toList();
  }

  @GetMapping("/categorias")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Devuelve las categorias existentes en el catalogo")
  public List<String> categorias() {
    return service.categorias();
  }

  @GetMapping("/{id}")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Obtiene un producto por su identificador")
  public ProductoResponse obtener(@PathVariable Long id) {
    return ProductoResponse.de(service.obtener(id));
  }

  @GetMapping("/sku/{sku}")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Obtiene un producto por su SKU")
  public ProductoResponse obtenerPorSku(@PathVariable String sku) {
    return ProductoResponse.de(service.obtenerPorSku(sku));
  }

  @PostMapping
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Crea un producto (solo rol Pedidos.Admin)")
  public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody CrearProductoRequest request) {
    ProductoResponse creado = ProductoResponse.de(service.crear(request));
    return ResponseEntity.created(URI.create("/api/v1/productos/" + creado.id())).body(creado);
  }

  @PutMapping("/{id}")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Actualiza los datos de un producto (solo rol Pedidos.Admin)")
  public ProductoResponse actualizar(
      @PathVariable Long id, @Valid @RequestBody ActualizarProductoRequest request) {
    return ProductoResponse.de(service.actualizar(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize(SecurityRoles.HAS_ADMIN)
  @Operation(summary = "Da de baja logica un producto (solo rol Pedidos.Admin)")
  public ResponseEntity<Void> desactivar(@PathVariable Long id) {
    service.desactivar(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/stock/descontar")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Descuenta stock al confirmar un pedido")
  public ProductoResponse descontarStock(
      @PathVariable Long id, @Valid @RequestBody MovimientoStockRequest request) {
    return ProductoResponse.de(service.descontarStock(id, request.cantidad(), request.motivo()));
  }

  @PostMapping("/{id}/stock/reponer")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Repone stock al anular un pedido o recibir una devolucion")
  public ProductoResponse reponerStock(
      @PathVariable Long id, @Valid @RequestBody MovimientoStockRequest request) {
    return ProductoResponse.de(service.reponerStock(id, request.cantidad(), request.motivo()));
  }
}
