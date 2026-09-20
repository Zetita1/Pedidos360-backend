package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.client.CatalogoClient;
import cl.duoc.pedidos360.pedidos.client.CatalogoClient.ProductoInfo;
import cl.duoc.pedidos360.pedidos.client.ClientesClient;
import cl.duoc.pedidos360.pedidos.client.ClientesClient.ClienteInfo;
import cl.duoc.pedidos360.pedidos.domain.DetallePedido;
import cl.duoc.pedidos360.pedidos.domain.EstadoPedido;
import cl.duoc.pedidos360.pedidos.domain.Pedido;
import cl.duoc.pedidos360.pedidos.repository.PedidoRepository;
import cl.duoc.pedidos360.pedidos.web.PedidoDtos.CrearPedidoRequest;
import cl.duoc.pedidos360.pedidos.web.PedidoDtos.LineaRequest;
import cl.duoc.pedidos360.pedidos.web.PedidoDtos.ResumenEstado;
import cl.duoc.pedidos360.web.RecursoNoEncontradoException;
import cl.duoc.pedidos360.web.ReglaNegocioException;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el ciclo de vida del pedido: valida el cliente contra ms-clientes, toma precios
 * y stock desde ms-productos y mantiene la consistencia de la maquina de estados.
 */
@Service
public class PedidoService {

  private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

  private final PedidoRepository repository;
  private final CatalogoClient catalogoClient;
  private final ClientesClient clientesClient;

  public PedidoService(
      PedidoRepository repository, CatalogoClient catalogoClient, ClientesClient clientesClient) {
    this.repository = repository;
    this.catalogoClient = catalogoClient;
    this.clientesClient = clientesClient;
  }

  @Transactional(readOnly = true)
  public List<Pedido> buscar(EstadoPedido estado, Long clienteId) {
    return repository.buscar(estado, clienteId);
  }

  @Transactional(readOnly = true)
  public List<Pedido> misPedidos(String usuarioOid) {
    return repository.findByUsuarioOidOrderByCreadoEnDesc(usuarioOid);
  }

  @Transactional(readOnly = true)
  public Pedido obtener(Long id) {
    return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("pedido", id));
  }

  @Transactional(readOnly = true)
  public List<ResumenEstado> resumenPorEstado() {
    return Arrays.stream(EstadoPedido.values())
        .map(estado -> new ResumenEstado(estado, repository.contarPorEstado(estado)))
        .toList();
  }

  /**
   * Registra un pedido en estado BORRADOR con los precios vigentes del catalogo.
   *
   * @param request datos del pedido
   * @param usuarioOid identidad del usuario que lo registra (claim oid)
   * @param usuarioNombre nombre legible del usuario
   * @return pedido persistido
   */
  @Transactional
  public Pedido crear(CrearPedidoRequest request, String usuarioOid, String usuarioNombre) {
    ClienteInfo cliente = clientesClient.obtener(request.clienteId());
    if (cliente == null) {
      throw new RecursoNoEncontradoException("cliente", request.clienteId());
    }
    if (!cliente.activo()) {
      throw new ReglaNegocioException(
          "El cliente " + cliente.rut() + " esta dado de baja y no puede generar pedidos");
    }

    String direccion =
        (request.direccionDespacho() == null || request.direccionDespacho().isBlank())
            ? cliente.direccionFormateada()
            : request.direccionDespacho();

    Pedido pedido =
        new Pedido(
            siguienteNumero(),
            cliente.id(),
            cliente.rut(),
            cliente.razonSocial(),
            usuarioOid,
            usuarioNombre,
            direccion,
            request.observacion());

    for (LineaRequest linea : consolidar(request.lineas())) {
      ProductoInfo producto = catalogoClient.obtener(linea.productoId());
      if (producto == null) {
        throw new RecursoNoEncontradoException("producto", linea.productoId());
      }
      if (!producto.activo()) {
        throw new ReglaNegocioException("El producto " + producto.sku() + " no esta disponible");
      }
      if (producto.stock() < linea.cantidad()) {
        throw new ReglaNegocioException(
            "Stock insuficiente para "
                + producto.sku()
                + ": disponible "
                + producto.stock()
                + ", solicitado "
                + linea.cantidad());
      }
      pedido.agregarDetalle(
          new DetallePedido(
              producto.id(),
              producto.sku(),
              producto.nombre(),
              linea.cantidad(),
              producto.precio()));
    }

    Pedido guardado = repository.save(pedido);
    log.info(
        "Pedido {} creado para cliente {} por usuario {} con total {}",
        guardado.getNumero(),
        cliente.rut(),
        usuarioOid,
        guardado.getTotal());
    return guardado;
  }

  /**
   * Confirma el pedido y descuenta el stock en el catalogo.
   *
   * @param id identificador del pedido
   * @return pedido confirmado
   */
  @Transactional
  public Pedido confirmar(Long id) {
    Pedido pedido = obtener(id);
    if (pedido.getEstado() != EstadoPedido.BORRADOR) {
      throw new ReglaNegocioException(
          "Solo se confirma un pedido en BORRADOR; el pedido esta en " + pedido.getEstado());
    }
    pedido.confirmar();
    pedido
        .getDetalles()
        .forEach(
            detalle ->
                catalogoClient.descontarStock(
                    detalle.getProductoId(),
                    detalle.getCantidad(),
                    "Confirmacion pedido " + pedido.getNumero()));
    log.info("Pedido {} confirmado", pedido.getNumero());
    return pedido;
  }

  /**
   * Aplica un cambio de estado validando la maquina de estados; al anular un pedido que ya
   * reservo stock, lo repone en el catalogo.
   *
   * @param id identificador del pedido
   * @param destino estado destino
   * @param motivo trazabilidad del cambio
   * @return pedido actualizado
   */
  @Transactional
  public Pedido cambiarEstado(Long id, EstadoPedido destino, String motivo) {
    Pedido pedido = obtener(id);
    if (destino == EstadoPedido.CONFIRMADO && pedido.getEstado() == EstadoPedido.BORRADOR) {
      return confirmar(id);
    }

    boolean devolverStock = destino == EstadoPedido.ANULADO && pedido.getEstado().reservaStock();
    pedido.cambiarEstado(destino);

    if (devolverStock) {
      pedido
          .getDetalles()
          .forEach(
              detalle ->
                  catalogoClient.reponerStock(
                      detalle.getProductoId(),
                      detalle.getCantidad(),
                      "Anulacion pedido " + pedido.getNumero()));
    }
    log.info("Pedido {} paso a estado {} (motivo: {})", pedido.getNumero(), destino, motivo);
    return pedido;
  }

  @Transactional
  public Pedido actualizarDespacho(Long id, String direccion, String observacion) {
    Pedido pedido = obtener(id);
    pedido.actualizarDespacho(direccion, observacion);
    return pedido;
  }

  /**
   * Genera el correlativo del pedido con el formato {@code PED-<anio>-<secuencia>}.
   *
   * @return numero de pedido
   */
  private String siguienteNumero() {
    String prefijo = "PED-" + Year.now().getValue() + "-";
    long correlativo = repository.contarPorPrefijo(prefijo) + 1;
    return prefijo + String.format("%05d", correlativo);
  }

  /**
   * Une las lineas repetidas del mismo producto para no duplicar el detalle.
   *
   * @param lineas lineas enviadas por el cliente
   * @return lineas consolidadas
   */
  private List<LineaRequest> consolidar(List<LineaRequest> lineas) {
    List<LineaRequest> consolidadas = new ArrayList<>();
    for (LineaRequest linea : lineas) {
      int indice = indiceDe(consolidadas, linea.productoId());
      if (indice >= 0) {
        LineaRequest existente = consolidadas.get(indice);
        consolidadas.set(
            indice, new LineaRequest(linea.productoId(), existente.cantidad() + linea.cantidad()));
      } else {
        consolidadas.add(linea);
      }
    }
    return consolidadas;
  }

  private int indiceDe(List<LineaRequest> lineas, Long productoId) {
    for (int i = 0; i < lineas.size(); i++) {
      if (lineas.get(i).productoId().equals(productoId)) {
        return i;
      }
    }
    return -1;
  }
}
