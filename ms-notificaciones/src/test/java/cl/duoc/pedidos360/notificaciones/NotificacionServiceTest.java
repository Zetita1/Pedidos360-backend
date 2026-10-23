package cl.duoc.pedidos360.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.duoc.pedidos360.mensajeria.consumo.EventoNoProcesableException;
import cl.duoc.pedidos360.mensajeria.evento.PedidoConfirmadoEvento;
import cl.duoc.pedidos360.mensajeria.evento.PedidoConfirmadoEvento.LineaEvento;
import cl.duoc.pedidos360.mensajeria.evento.PedidoCreadoEvento;
import cl.duoc.pedidos360.mensajeria.evento.PedidoEstadoCambiadoEvento;
import cl.duoc.pedidos360.notificaciones.domain.Documento;
import cl.duoc.pedidos360.notificaciones.domain.Notificacion;
import cl.duoc.pedidos360.notificaciones.domain.TipoNotificacion;
import cl.duoc.pedidos360.notificaciones.service.DocumentoService;
import cl.duoc.pedidos360.notificaciones.service.NotificacionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Logica de negocio de los consumidores: que generan, como reaccionan a un evento invalido y
 * que pasa cuando RabbitMQ entrega dos veces el mismo mensaje.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificacionServiceTest {

  @Autowired private NotificacionService notificaciones;
  @Autowired private DocumentoService documentos;

  private PedidoCreadoEvento pedidoCreado(String pedido) {
    return PedidoCreadoEvento.de(
        pedido, 1L, 10L, "Comercial Andes SpA", "contacto@andes.cl", new BigDecimal("238000"), 2);
  }

  @Test
  @DisplayName("Un pedido creado genera la notificacion de recepcion")
  void pedidoCreadoGeneraNotificacion() {
    notificaciones.registrarRecepcion(pedidoCreado("PED-2026-00100"));

    List<Notificacion> generadas = notificaciones.porPedido("PED-2026-00100");
    assertThat(generadas).hasSize(1);
    assertThat(generadas.get(0).getTipo()).isEqualTo(TipoNotificacion.RECEPCION);
    assertThat(generadas.get(0).getDestinatario()).isEqualTo("contacto@andes.cl");
    assertThat(generadas.get(0).getMensaje()).contains("PED-2026-00100");
  }

  @Test
  @DisplayName("El mismo evento entregado dos veces no duplica la notificacion")
  void eventoRepetidoNoDuplica() {
    PedidoCreadoEvento evento = pedidoCreado("PED-2026-00101");

    notificaciones.registrarRecepcion(evento);
    notificaciones.registrarRecepcion(evento);

    assertThat(notificaciones.porPedido("PED-2026-00101")).hasSize(1);
  }

  @Test
  @DisplayName("Un evento sin numero de pedido es no procesable y no se reintenta")
  void eventoSinPedidoEsNoProcesable() {
    PedidoCreadoEvento invalido =
        new PedidoCreadoEvento(
            "evt-sin-pedido", Instant.now(), "  ", 1L, 10L, "Cliente", "a@b.cl", BigDecimal.TEN, 1);

    assertThatThrownBy(() -> notificaciones.registrarRecepcion(invalido))
        .isInstanceOf(EventoNoProcesableException.class)
        .hasMessageContaining("sin numero de pedido");
  }

  @Test
  @DisplayName("Un cambio a CONFIRMADO se clasifica como confirmacion")
  void cambioAConfirmadoSeClasifica() {
    notificaciones.registrarSeguimiento(
        PedidoEstadoCambiadoEvento.de(
            "PED-2026-00102", 2L, 10L, "contacto@andes.cl", "BORRADOR", "CONFIRMADO", null));

    assertThat(notificaciones.porPedido("PED-2026-00102").get(0).getTipo())
        .isEqualTo(TipoNotificacion.CONFIRMACION);
  }

  @Test
  @DisplayName("Un cambio de estado sin estado nuevo es no procesable")
  void cambioSinEstadoEsNoProcesable() {
    PedidoEstadoCambiadoEvento invalido =
        new PedidoEstadoCambiadoEvento(
            "evt-sin-estado",
            Instant.now(),
            "PED-2026-00103",
            3L,
            10L,
            "contacto@andes.cl",
            "BORRADOR",
            null,
            null);

    assertThatThrownBy(() -> notificaciones.registrarSeguimiento(invalido))
        .isInstanceOf(EventoNoProcesableException.class)
        .hasMessageContaining("estado nuevo");
  }

  @Test
  @DisplayName("Un pedido confirmado emite su ticket con folio y detalle")
  void pedidoConfirmadoEmiteTicket() {
    documentos.emitirTicket(
        PedidoConfirmadoEvento.de(
            "PED-2026-00104",
            4L,
            10L,
            "Comercial Andes SpA",
            "contacto@andes.cl",
            new BigDecimal("119000"),
            List.of(
                new LineaEvento(
                    "NB-001",
                    "Notebook Pro 14",
                    1,
                    new BigDecimal("100000"),
                    new BigDecimal("100000")))));

    Documento ticket = documentos.porPedido("PED-2026-00104");
    assertThat(ticket.getFolio()).startsWith("TCK-");
    assertThat(ticket.getContenido()).contains("NB-001").contains("PED-2026-00104");
  }

  @Test
  @DisplayName("Un pedido confirmado sin lineas no genera ticket y no se reintenta")
  void pedidoConfirmadoSinLineasEsNoProcesable() {
    PedidoConfirmadoEvento sinLineas =
        PedidoConfirmadoEvento.de(
            "PED-2026-00105", 5L, 10L, "Comercial Andes SpA", "a@b.cl", BigDecimal.TEN, List.of());

    assertThatThrownBy(() -> documentos.emitirTicket(sinLineas))
        .isInstanceOf(EventoNoProcesableException.class)
        .hasMessageContaining("sin lineas");
  }
}
