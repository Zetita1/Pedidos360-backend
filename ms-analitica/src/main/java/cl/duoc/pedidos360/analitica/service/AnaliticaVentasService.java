package cl.duoc.pedidos360.analitica.service;

import cl.duoc.pedidos360.analitica.config.AnaliticaProperties;
import cl.duoc.pedidos360.analitica.domain.EventoProcesado;
import cl.duoc.pedidos360.analitica.domain.MetricaDiaria;
import cl.duoc.pedidos360.analitica.domain.VentaPorCliente;
import cl.duoc.pedidos360.analitica.repository.EventoProcesadoRepository;
import cl.duoc.pedidos360.analitica.repository.MetricaDiariaRepository;
import cl.duoc.pedidos360.analitica.repository.VentaPorClienteRepository;
import cl.duoc.pedidos360.streaming.evento.PedidoStreamEvento;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Construye las metricas de venta a partir de los hechos de pedidos.
 *
 * <p>Este servicio no sabe que los hechos llegan por Kafka: recibe el evento ya deserializado
 * y validado, junto a su origen, y se ocupa solo de lo que significa para el negocio. Toda la
 * mecanica de consumo —offsets, reintentos, descarte— vive en el listener.
 */
@Service
public class AnaliticaVentasService {

  private static final Logger log = LoggerFactory.getLogger(AnaliticaVentasService.class);

  private final MetricaDiariaRepository metricas;
  private final VentaPorClienteRepository clientes;
  private final EventoProcesadoRepository eventos;
  private final AnaliticaProperties propiedades;

  public AnaliticaVentasService(
      MetricaDiariaRepository metricas,
      VentaPorClienteRepository clientes,
      EventoProcesadoRepository eventos,
      AnaliticaProperties propiedades) {
    this.metricas = metricas;
    this.clientes = clientes;
    this.eventos = eventos;
    this.propiedades = propiedades;
  }

  /**
   * Aplica un hecho de pedido a las metricas.
   *
   * <p>La operacion es una sola transaccion: o se suma a las metricas y queda registrado el
   * evento, o no ocurre ninguna de las dos cosas. Si fallara a medio camino, el registro se
   * volveria a entregar y se sumaria dos veces.
   *
   * @param evento hecho de pedido recibido
   * @param origen coordenadas del registro en el topico
   * @return true si se aplico, false si el evento ya estaba procesado
   */
  @Transactional
  public boolean procesar(PedidoStreamEvento evento, OrigenRegistro origen) {
    if (eventos.existsByEventoId(evento.eventoId())) {
      log.debug(
          "Evento {} repetido en {}: ya estaba procesado, se descarta",
          evento.eventoId(),
          origen.ubicacion());
      return false;
    }

    LocalDate dia = LocalDate.ofInstant(evento.ocurridoEn(), propiedades.zona());
    MetricaDiaria metrica =
        metricas.findByFecha(dia).orElseGet(() -> metricas.save(new MetricaDiaria(dia)));

    switch (evento.tipo()) {
      case CREADO -> metrica.registrarCreado(evento.total());
      case CONFIRMADO -> {
        metrica.registrarConfirmado(evento.total(), evento.cantidadLineas());
        acumularEnCliente(evento);
      }
      case ANULADO -> metrica.registrarAnulado(evento.total());
      case ESTADO_CAMBIADO -> metrica.registrarCambioEstado();
    }

    eventos.save(
        new EventoProcesado(
            evento.eventoId(),
            origen.topico(),
            origen.particion(),
            origen.offset(),
            origen.clave(),
            evento.tipo().name()));

    log.info(
        "Metricas del {} actualizadas por {} del pedido {} leido en {}",
        dia,
        evento.tipo(),
        evento.pedidoNumero(),
        origen.ubicacion());
    return true;
  }

  /**
   * Suma la compra al acumulado del cliente.
   *
   * @param evento hecho de confirmacion
   */
  private void acumularEnCliente(PedidoStreamEvento evento) {
    VentaPorCliente acumulado =
        clientes
            .findById(evento.clienteId())
            .orElseGet(
                () ->
                    clientes.save(
                        new VentaPorCliente(evento.clienteId(), evento.clienteNombre())));
    acumulado.sumar(evento.total(), evento.ocurridoEn(), evento.clienteNombre());
  }

  /**
   * Metricas de los ultimos dias.
   *
   * @param dias cuantos dias hacia atras incluir
   * @return metricas del periodo, de la mas reciente a la mas antigua
   */
  @Transactional(readOnly = true)
  public List<MetricaDiaria> ultimosDias(int dias) {
    LocalDate desde = LocalDate.now(propiedades.zona()).minusDays(Math.max(dias, 1) - 1L);
    return metricas.findByFechaGreaterThanEqualOrderByFechaDesc(desde);
  }

  /**
   * Metrica del dia en curso, creandola vacia si todavia no hay hechos.
   *
   * @return metrica de hoy
   */
  @Transactional(readOnly = true)
  public MetricaDiaria hoy() {
    LocalDate dia = LocalDate.now(propiedades.zona());
    return metricas.findByFecha(dia).orElseGet(() -> new MetricaDiaria(dia));
  }

  /**
   * Ranking de clientes por monto comprado.
   *
   * @param limite cuantos devolver
   * @return clientes ordenados por monto
   */
  @Transactional(readOnly = true)
  public List<VentaPorCliente> mejoresClientes(int limite) {
    return clientes.findAllByOrderByMontoTotalDesc(PageRequest.of(0, Math.max(limite, 1)));
  }
}
