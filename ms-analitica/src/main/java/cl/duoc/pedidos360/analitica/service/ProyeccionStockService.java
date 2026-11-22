package cl.duoc.pedidos360.analitica.service;

import cl.duoc.pedidos360.analitica.config.AnaliticaProperties;
import cl.duoc.pedidos360.analitica.domain.AlertaStock;
import cl.duoc.pedidos360.analitica.domain.EstadoStock;
import cl.duoc.pedidos360.analitica.domain.EventoProcesado;
import cl.duoc.pedidos360.analitica.repository.AlertaStockRepository;
import cl.duoc.pedidos360.analitica.repository.EstadoStockRepository;
import cl.duoc.pedidos360.analitica.repository.EventoProcesadoRepository;
import cl.duoc.pedidos360.streaming.evento.StockStreamEvento;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantiene el stock vigente de cada producto y dispara los avisos cuando se vuelve critico.
 *
 * <p>El aviso es el proceso derivado del streaming: nadie lo pide ni lo programa, lo provoca
 * la llegada de un registro al topico. Es la diferencia entre revisar una pantalla y que el
 * sistema reaccione al hecho.
 */
@Service
public class ProyeccionStockService {

  private static final Logger log = LoggerFactory.getLogger(ProyeccionStockService.class);

  private final EstadoStockRepository estados;
  private final AlertaStockRepository alertas;
  private final EventoProcesadoRepository eventos;
  private final AnaliticaProperties propiedades;

  public ProyeccionStockService(
      EstadoStockRepository estados,
      AlertaStockRepository alertas,
      EventoProcesadoRepository eventos,
      AnaliticaProperties propiedades) {
    this.estados = estados;
    this.alertas = alertas;
    this.eventos = eventos;
    this.propiedades = propiedades;
  }

  /**
   * Aplica un movimiento de stock a la proyeccion.
   *
   * @param evento movimiento recibido
   * @param origen coordenadas del registro en el topico
   * @return true si se aplico, false si el evento ya estaba procesado
   */
  @Transactional
  public boolean procesar(StockStreamEvento evento, OrigenRegistro origen) {
    if (eventos.existsByEventoId(evento.eventoId())) {
      log.debug(
          "Movimiento {} repetido en {}: ya estaba procesado, se descarta",
          evento.eventoId(),
          origen.ubicacion());
      return false;
    }

    EstadoStock estado =
        estados
            .findById(evento.productoId())
            .orElseGet(
                () -> estados.save(new EstadoStock(evento.productoId(), evento.sku())));
    Integer stockPrevio = estado.getStock();
    estado.aplicar(
        evento.sku(),
        evento.nombre(),
        evento.categoria(),
        evento.precio(),
        evento.stockAnterior(),
        evento.stockActual(),
        evento.motivo(),
        evento.ocurridoEn());

    avisarSiEsCritico(evento, stockPrevio);

    eventos.save(
        new EventoProcesado(
            evento.eventoId(),
            origen.topico(),
            origen.particion(),
            origen.offset(),
            origen.clave(),
            "STOCK"));

    log.info(
        "Stock del producto {} ({}) proyectado en {} unidades, leido en {}",
        evento.sku(),
        evento.productoId(),
        evento.stockActual(),
        origen.ubicacion());
    return true;
  }

  /**
   * Genera el aviso solo cuando el producto <i>entra</i> en zona critica.
   *
   * <p>Si avisara en cada registro mientras el stock siga bajo, una venta de dos unidades
   * generaria dos avisos del mismo producto y la lista dejaria de ser util. Importa el cruce
   * del umbral, no la permanencia bajo el.
   *
   * @param evento movimiento recibido
   * @param stockPrevio stock que tenia la proyeccion antes de aplicar el movimiento
   */
  private void avisarSiEsCritico(StockStreamEvento evento, Integer stockPrevio) {
    int umbral = propiedades.getUmbralStockCritico();
    boolean ahoraCritico = evento.stockActual() <= umbral;
    boolean antesCritico = stockPrevio != null && stockPrevio <= umbral;
    if (!ahoraCritico || antesCritico) {
      return;
    }
    alertas.save(
        new AlertaStock(
            evento.productoId(),
            evento.sku(),
            evento.nombre(),
            evento.stockActual(),
            umbral,
            evento.eventoId()));
    log.warn(
        "Aviso generado: el producto {} quedo en {} unidades (umbral {})",
        evento.sku(),
        evento.stockActual(),
        umbral);
  }

  /**
   * Proyeccion completa del stock.
   *
   * @return estado de todos los productos conocidos
   */
  @Transactional(readOnly = true)
  public List<EstadoStock> proyeccion() {
    return estados.findAllByOrderByActualizadoEnDesc();
  }

  /**
   * Productos en o bajo el umbral critico.
   *
   * @return productos criticos, primero los mas escasos
   */
  @Transactional(readOnly = true)
  public List<EstadoStock> criticos() {
    return estados.findByStockLessThanEqualOrderByStockAsc(
        propiedades.getUmbralStockCritico());
  }

  /**
   * Ultimos avisos generados.
   *
   * @param limite cuantos devolver
   * @return avisos mas recientes primero
   */
  @Transactional(readOnly = true)
  public List<AlertaStock> ultimasAlertas(int limite) {
    return alertas.findAllByOrderByGeneradaEnDesc(PageRequest.of(0, Math.max(limite, 1)));
  }

  /**
   * Valor total del inventario proyectado.
   *
   * @return suma de precio por unidades
   */
  @Transactional(readOnly = true)
  public BigDecimal valorizado() {
    return estados.valorizado();
  }

  /**
   * Cuantos productos estan en zona critica.
   *
   * @return cantidad de productos criticos
   */
  @Transactional(readOnly = true)
  public long cantidadCriticos() {
    return estados.contarCriticos(propiedades.getUmbralStockCritico());
  }

  /**
   * Umbral vigente.
   *
   * @return unidades a partir de las cuales el stock se considera critico
   */
  public int umbral() {
    return propiedades.getUmbralStockCritico();
  }
}
