package cl.duoc.pedidos360.notificaciones.service;

import cl.duoc.pedidos360.mensajeria.consumo.EventoNoProcesableException;
import cl.duoc.pedidos360.mensajeria.evento.PedidoConfirmadoEvento;
import cl.duoc.pedidos360.mensajeria.evento.PedidoConfirmadoEvento.LineaEvento;
import cl.duoc.pedidos360.notificaciones.domain.Documento;
import cl.duoc.pedidos360.notificaciones.repository.DocumentoRepository;
import cl.duoc.pedidos360.web.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite el ticket de los pedidos confirmados.
 *
 * <p>Es el trabajo pesado que se saca del camino del usuario: cuando alguien confirma un
 * pedido, la API responde de inmediato y el documento se genera despues, al consumir el
 * evento.
 */
@Service
public class DocumentoService {

  private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

  private static final DateTimeFormatter FECHA =
      DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.of("America/Santiago"));

  private final DocumentoRepository repository;

  public DocumentoService(DocumentoRepository repository) {
    this.repository = repository;
  }

  /**
   * Emite el ticket de un pedido confirmado.
   *
   * @param evento evento de pedido confirmado
   */
  @Transactional
  public void emitirTicket(PedidoConfirmadoEvento evento) {
    if (evento.pedidoNumero() == null || evento.pedidoNumero().isBlank()) {
      throw new EventoNoProcesableException(
          "El evento " + evento.eventoId() + " llego sin numero de pedido");
    }
    if (evento.detalles() == null || evento.detalles().isEmpty()) {
      throw new EventoNoProcesableException(
          "El pedido " + evento.pedidoNumero() + " llego sin lineas: no hay ticket que emitir");
    }
    if (repository.existsByEventoId(evento.eventoId())) {
      log.info(
          "El evento {} del pedido {} ya tenia ticket; se omite el duplicado",
          evento.eventoId(),
          evento.pedidoNumero());
      return;
    }

    String folio = siguienteFolio();
    Documento documento =
        new Documento(
            evento.eventoId(),
            folio,
            evento.pedidoNumero(),
            evento.pedidoId(),
            evento.clienteId(),
            evento.clienteNombre(),
            evento.total() == null ? BigDecimal.ZERO : evento.total(),
            renderizar(folio, evento));

    repository.save(documento);
    log.info("Ticket {} emitido para el pedido {}", folio, evento.pedidoNumero());
  }

  /**
   * Ultimos documentos emitidos.
   *
   * @param limite cantidad maxima a devolver
   * @return documentos del mas reciente al mas antiguo
   */
  @Transactional(readOnly = true)
  public List<Documento> ultimos(int limite) {
    return repository.findAllByOrderByCreadoEnDesc(Limit.of(limite));
  }

  /**
   * Documento de un pedido.
   *
   * @param pedidoNumero correlativo del pedido
   * @return documento emitido
   */
  @Transactional(readOnly = true)
  public Documento porPedido(String pedidoNumero) {
    return repository
        .findByPedidoNumero(pedidoNumero)
        .orElseThrow(() -> new RecursoNoEncontradoException("documento del pedido", pedidoNumero));
  }

  /**
   * Correlativo del ticket con el formato {@code TCK-<anio>-<secuencia>}.
   *
   * @return folio nuevo
   */
  private String siguienteFolio() {
    String prefijo = "TCK-" + Year.now().getValue() + "-";
    long correlativo = repository.countByFolioStartingWith(prefijo) + 1;
    return prefijo + String.format("%05d", correlativo);
  }

  /**
   * Arma el ticket como texto plano, listo para mostrarse o imprimirse.
   *
   * @param folio numero del ticket
   * @param evento evento de pedido confirmado
   * @return ticket renderizado
   */
  private String renderizar(String folio, PedidoConfirmadoEvento evento) {
    StringBuilder ticket = new StringBuilder();
    ticket.append("PEDIDOS360 - TICKET DE PEDIDO\n");
    ticket.append("=================================================\n");
    ticket.append("Folio    : ").append(folio).append('\n');
    ticket.append("Pedido   : ").append(evento.pedidoNumero()).append('\n');
    ticket.append("Cliente  : ").append(textoOGuion(evento.clienteNombre())).append('\n');
    ticket.append("Contacto : ").append(textoOGuion(evento.clienteEmail())).append('\n');
    ticket.append("Emitido  : ").append(FECHA.format(evento.ocurridoEn())).append('\n');
    ticket.append("-------------------------------------------------\n");
    ticket.append(String.format(Locale.ROOT, "%-10s %-22s %4s %12s%n", "SKU", "PRODUCTO", "CANT", "SUBTOTAL"));

    for (LineaEvento linea : evento.detalles()) {
      ticket.append(
          String.format(
              Locale.ROOT,
              "%-10s %-22s %4d %12s%n",
              recortar(linea.sku(), 10),
              recortar(linea.descripcion(), 22),
              linea.cantidad(),
              linea.subtotal()));
    }

    ticket.append("-------------------------------------------------\n");
    ticket.append(String.format(Locale.ROOT, "%40s %s%n", "TOTAL:", evento.total()));
    ticket.append("=================================================\n");
    ticket.append("Documento generado de forma asincrona desde la cola de documentos.\n");
    return ticket.toString();
  }

  private String textoOGuion(String valor) {
    return (valor == null || valor.isBlank()) ? "-" : valor;
  }

  private String recortar(String texto, int largo) {
    if (texto == null) {
      return "-";
    }
    return texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
  }
}
