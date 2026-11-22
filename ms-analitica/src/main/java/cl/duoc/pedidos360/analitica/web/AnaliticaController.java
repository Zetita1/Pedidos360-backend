package cl.duoc.pedidos360.analitica.web;

import cl.duoc.pedidos360.analitica.domain.MetricaDiaria;
import cl.duoc.pedidos360.analitica.repository.EventoProcesadoRepository;
import cl.duoc.pedidos360.analitica.service.AnaliticaVentasService;
import cl.duoc.pedidos360.analitica.service.ProyeccionStockService;
import cl.duoc.pedidos360.analitica.web.AnaliticaDtos.AlertaResponse;
import cl.duoc.pedidos360.analitica.web.AnaliticaDtos.EstadoStockResponse;
import cl.duoc.pedidos360.analitica.web.AnaliticaDtos.EventoResponse;
import cl.duoc.pedidos360.analitica.web.AnaliticaDtos.MetricaDiariaResponse;
import cl.duoc.pedidos360.analitica.web.AnaliticaDtos.ResumenResponse;
import cl.duoc.pedidos360.analitica.web.AnaliticaDtos.VentaClienteResponse;
import cl.duoc.pedidos360.security.SecurityRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de las proyecciones construidas desde los topicos de Kafka.
 *
 * <p>Es un API de solo lectura. Nada de lo que devuelve se puede crear ni modificar por HTTP:
 * cada cifra salio de un registro de un topico, y la unica forma de cambiarla es publicar un
 * hecho nuevo.
 */
@RestController
@RequestMapping("/api/v1/analitica")
@Tag(name = "Analitica", description = "Metricas y proyecciones construidas desde Kafka")
@SecurityRequirement(name = "entra-id")
@Validated
public class AnaliticaController {

  private final AnaliticaVentasService ventas;
  private final ProyeccionStockService stock;
  private final EventoProcesadoRepository eventos;

  public AnaliticaController(
      AnaliticaVentasService ventas,
      ProyeccionStockService stock,
      EventoProcesadoRepository eventos) {
    this.ventas = ventas;
    this.stock = stock;
    this.eventos = eventos;
  }

  @GetMapping("/resumen")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Cifras del dia en curso y estado del inventario proyectado")
  public ResumenResponse resumen() {
    MetricaDiaria hoy = ventas.hoy();
    return new ResumenResponse(
        hoy.getFecha(),
        hoy.getPedidosCreados(),
        hoy.getPedidosConfirmados(),
        hoy.getPedidosAnulados(),
        hoy.getMontoConfirmado(),
        hoy.ticketPromedio(),
        hoy.getLineasVendidas(),
        stock.proyeccion().size(),
        stock.cantidadCriticos(),
        stock.umbral(),
        stock.valorizado(),
        eventos.count(),
        hoy.getActualizadoEn());
  }

  @GetMapping("/ventas/diarias")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Metricas por dia de los ultimos dias")
  public List<MetricaDiariaResponse> diarias(
      @RequestParam(defaultValue = "7") @Positive @Max(90) int dias) {
    return ventas.ultimosDias(dias).stream().map(MetricaDiariaResponse::de).toList();
  }

  @GetMapping("/ventas/clientes")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Clientes ordenados por monto comprado")
  public List<VentaClienteResponse> clientes(
      @RequestParam(defaultValue = "10") @Positive @Max(100) int limite) {
    return ventas.mejoresClientes(limite).stream().map(VentaClienteResponse::de).toList();
  }

  @GetMapping("/stock")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Stock vigente proyectado desde el topico compactado")
  public List<EstadoStockResponse> stock(
      @RequestParam(defaultValue = "false") boolean soloCriticos) {
    int umbral = stock.umbral();
    return (soloCriticos ? stock.criticos() : stock.proyeccion()).stream()
        .map(estado -> EstadoStockResponse.de(estado, umbral))
        .toList();
  }

  @GetMapping("/alertas")
  @PreAuthorize(SecurityRoles.HAS_ANY)
  @Operation(summary = "Avisos de stock critico generados por el consumidor")
  public List<AlertaResponse> alertas(
      @RequestParam(defaultValue = "20") @Positive @Max(200) int limite) {
    return stock.ultimasAlertas(limite).stream().map(AlertaResponse::de).toList();
  }

  @GetMapping("/eventos")
  @PreAuthorize(SecurityRoles.HAS_GESTION)
  @Operation(summary = "Ultimos registros consumidos, con topico, particion y offset")
  public List<EventoResponse> eventos(
      @RequestParam(defaultValue = "30") @Positive @Max(200) int limite) {
    return eventos
        .findAllByOrderByProcesadoEnDesc(PageRequest.of(0, limite))
        .stream()
        .map(EventoResponse::de)
        .toList();
  }
}
