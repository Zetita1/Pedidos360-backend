package cl.duoc.pedidos360.streaming.consumo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.duoc.pedidos360.streaming.evento.PedidoStreamEvento;
import cl.duoc.pedidos360.streaming.evento.StockStreamEvento;
import cl.duoc.pedidos360.streaming.evento.TipoHechoPedido;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageHeaders;

/**
 * Validacion de los registros entrantes: lo que pasa y lo que se descarta antes de llegar a
 * la logica de negocio.
 */
class ValidadorEventosTest {

  private static ValidatorFactory fabrica;
  private static ValidadorEventos validador;

  @BeforeAll
  static void prepararValidador() {
    fabrica = Validation.buildDefaultValidatorFactory();
    Validator validator = fabrica.getValidator();
    validador = new ValidadorEventos(validator);
  }

  @AfterAll
  static void cerrarValidador() {
    fabrica.close();
  }

  private static MessageHeaders cabeceras() {
    return new MessageHeaders(
        Map.of(
            KafkaHeaders.RECEIVED_TOPIC, "pedidos360.pedidos.eventos",
            KafkaHeaders.RECEIVED_PARTITION, 1,
            KafkaHeaders.OFFSET, 42L));
  }

  @Test
  @DisplayName("Un hecho de pedido completo pasa la validacion")
  void eventoValidoPasa() {
    PedidoStreamEvento evento =
        PedidoStreamEvento.creado(
            "PED-2026-00001", 1L, 10L, "Comercial Andes SpA", new BigDecimal("238000"), 2, "oid-1");

    assertThatCode(() -> validador.validar(evento, cabeceras())).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Un hecho sin numero de pedido se descarta como no procesable")
  void sinNumeroDePedidoSeDescarta() {
    PedidoStreamEvento invalido =
        new PedidoStreamEvento(
            "evt-1",
            TipoHechoPedido.CREADO,
            "   ",
            1L,
            10L,
            "Comercial Andes SpA",
            new BigDecimal("1000"),
            1,
            null,
            "BORRADOR",
            null,
            "oid-1",
            Instant.now());

    assertThatThrownBy(() -> validador.validar(invalido, cabeceras()))
        .isInstanceOf(EventoStreamInvalidoException.class)
        .hasMessageContaining("pedidoNumero");
  }

  @Test
  @DisplayName("Un movimiento de stock con total negativo se descarta")
  void stockNegativoSeDescarta() {
    StockStreamEvento invalido =
        new StockStreamEvento(
            "evt-2",
            7L,
            "SKU-7",
            "Teclado mecanico",
            "Perifericos",
            new BigDecimal("39990"),
            5,
            -3,
            "Ajuste",
            Instant.now());

    assertThatThrownBy(() -> validador.validar(invalido, cabeceras()))
        .isInstanceOf(EventoStreamInvalidoException.class)
        .hasMessageContaining("stockActual");
  }

  @Test
  @DisplayName("Un registro vacio se descarta nombrando su ubicacion en el topico")
  void registroVacioSeDescarta() {
    assertThatThrownBy(() -> validador.validar(null, cabeceras()))
        .isInstanceOf(EventoStreamInvalidoException.class)
        .hasMessageContaining("pedidos360.pedidos.eventos-1@42");
  }

  @Test
  @DisplayName("La ubicacion del registro se arma con topico, particion y offset")
  void ubicacionSeArmaConCoordenadas() {
    assertThat(ValidadorEventos.ubicacion(cabeceras()))
        .isEqualTo("pedidos360.pedidos.eventos-1@42");
  }

  @Test
  @DisplayName("El movimiento de stock calcula el delta contra el valor anterior")
  void deltaSeCalculaContraElAnterior() {
    StockStreamEvento movimiento =
        StockStreamEvento.de(
            7L, "SKU-7", "Teclado", "Perifericos", new BigDecimal("39990"), 12, 9, "Venta");

    assertThat(movimiento.delta()).isEqualTo(-3);
    assertThat(movimiento.clave()).isEqualTo("7");
  }
}
