package cl.duoc.pedidos360.analitica;

import static org.assertj.core.api.Assertions.assertThat;

import cl.duoc.pedidos360.analitica.domain.AlertaStock;
import cl.duoc.pedidos360.analitica.domain.EstadoStock;
import cl.duoc.pedidos360.analitica.domain.MetricaDiaria;
import cl.duoc.pedidos360.analitica.service.AnaliticaVentasService;
import cl.duoc.pedidos360.analitica.service.OrigenRegistro;
import cl.duoc.pedidos360.analitica.service.ProyeccionStockService;
import cl.duoc.pedidos360.streaming.evento.PedidoStreamEvento;
import cl.duoc.pedidos360.streaming.evento.StockStreamEvento;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Logica de los consumidores de streaming: que calculan, que pasa cuando Kafka entrega el
 * mismo registro dos veces y cuando se dispara el aviso de stock.
 *
 * <p>No hay cluster en estas pruebas: se llama a los servicios de negocio directamente, que
 * es justamente lo que permite tener la logica separada de la mensajeria.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnaliticaStreamingTest {

  @Autowired private AnaliticaVentasService ventas;
  @Autowired private ProyeccionStockService stock;

  private static OrigenRegistro origen(int particion, long offset) {
    return new OrigenRegistro("pedidos360.pedidos.eventos", particion, offset, "PED-X");
  }

  private static OrigenRegistro origenStock(long offset) {
    return new OrigenRegistro("pedidos360.inventario.stock", 0, offset, "7");
  }

  @Test
  @DisplayName("Un pedido confirmado suma a la venta del dia y al acumulado del cliente")
  void confirmadoSumaVentaYCliente() {
    PedidoStreamEvento evento =
        PedidoStreamEvento.confirmado(
            "PED-2026-00500",
            500L,
            77L,
            "Comercial Andes SpA",
            new BigDecimal("150000"),
            3,
            "oid-vendedor");

    assertThat(ventas.procesar(evento, origen(0, 10))).isTrue();

    MetricaDiaria hoy = ventas.hoy();
    assertThat(hoy.getPedidosConfirmados()).isPositive();
    assertThat(hoy.getMontoConfirmado()).isGreaterThanOrEqualTo(new BigDecimal("150000"));
    assertThat(hoy.ticketPromedio()).isPositive();

    assertThat(ventas.mejoresClientes(10))
        .anySatisfy(
            cliente -> {
              assertThat(cliente.getClienteId()).isEqualTo(77L);
              assertThat(cliente.getPedidosConfirmados()).isPositive();
            });
  }

  @Test
  @DisplayName("El mismo registro entregado dos veces se cuenta una sola vez")
  void registroRepetidoNoSeCuentaDosVeces() {
    PedidoStreamEvento evento =
        PedidoStreamEvento.creado(
            "PED-2026-00501", 501L, 78L, "Importadora Sur", new BigDecimal("90000"), 1, "oid-1");

    int creadosAntes = ventas.hoy().getPedidosCreados();

    assertThat(ventas.procesar(evento, origen(1, 20))).isTrue();
    // Kafka garantiza entrega al menos una vez: el segundo intento llega con el mismo
    // eventoId y distinto offset, y no debe volver a sumar.
    assertThat(ventas.procesar(evento, origen(1, 21))).isFalse();

    assertThat(ventas.hoy().getPedidosCreados()).isEqualTo(creadosAntes + 1);
  }

  @Test
  @DisplayName("Una anulacion suma al monto anulado y no a la venta confirmada")
  void anulacionSumaAlMontoAnulado() {
    BigDecimal confirmadoAntes = ventas.hoy().getMontoConfirmado();
    PedidoStreamEvento anulado =
        PedidoStreamEvento.estadoCambiado(
            "PED-2026-00502",
            502L,
            79L,
            "Distribuidora Norte",
            new BigDecimal("50000"),
            2,
            "CONFIRMADO",
            "ANULADO",
            "Cliente desistio",
            "oid-admin");

    assertThat(ventas.procesar(anulado, origen(2, 30))).isTrue();

    assertThat(ventas.hoy().getMontoAnulado()).isGreaterThanOrEqualTo(new BigDecimal("50000"));
    assertThat(ventas.hoy().getMontoConfirmado()).isEqualByComparingTo(confirmadoAntes);
  }

  @Test
  @DisplayName("El ultimo movimiento de stock reemplaza al anterior en la proyeccion")
  void stockSeProyectaConElUltimoValor() {
    stock.procesar(
        StockStreamEvento.de(
            901L, "SKU-901", "Monitor 24", "Pantallas", new BigDecimal("129990"), 100, 80, "Venta"),
        origenStock(1));
    stock.procesar(
        StockStreamEvento.de(
            901L, "SKU-901", "Monitor 24", "Pantallas", new BigDecimal("129990"), 80, 65, "Venta"),
        origenStock(2));

    EstadoStock proyectado =
        stock.proyeccion().stream()
            .filter(estado -> estado.getProductoId().equals(901L))
            .findFirst()
            .orElseThrow();

    assertThat(proyectado.getStock()).isEqualTo(65);
    assertThat(proyectado.getStockAnterior()).isEqualTo(80);
    assertThat(proyectado.valorizado()).isEqualByComparingTo(new BigDecimal("8449350"));
  }

  @Test
  @DisplayName("El aviso de stock se genera al cruzar el umbral, no cada vez que sigue bajo")
  void avisoSeGeneraSoloAlCruzarElUmbral() {
    int umbral = stock.umbral();

    // Sobre el umbral: no corresponde avisar.
    stock.procesar(
        StockStreamEvento.de(
            902L, "SKU-902", "Mouse", "Perifericos", new BigDecimal("9990"), 40, umbral + 5, "Venta"),
        origenStock(10));
    assertThat(avisosDe(902L)).isEmpty();

    // Cruza el umbral: aqui se avisa.
    stock.procesar(
        StockStreamEvento.de(
            902L, "SKU-902", "Mouse", "Perifericos", new BigDecimal("9990"), umbral + 5, umbral - 1,
            "Venta"),
        origenStock(11));
    assertThat(avisosDe(902L)).hasSize(1);

    // Sigue bajo el umbral: no se repite el aviso.
    stock.procesar(
        StockStreamEvento.de(
            902L, "SKU-902", "Mouse", "Perifericos", new BigDecimal("9990"), umbral - 1, umbral - 2,
            "Venta"),
        origenStock(12));
    assertThat(avisosDe(902L)).hasSize(1);

    assertThat(stock.criticos()).anySatisfy(estado -> assertThat(estado.getStock()).isLessThanOrEqualTo(umbral));
  }

  private List<AlertaStock> avisosDe(Long productoId) {
    return stock.ultimasAlertas(200).stream()
        .filter(alerta -> alerta.getProductoId().equals(productoId))
        .toList();
  }
}
