package cl.duoc.pedidos360.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.duoc.pedidos360.pedidos.domain.DetallePedido;
import cl.duoc.pedidos360.pedidos.domain.EstadoPedido;
import cl.duoc.pedidos360.pedidos.domain.Pedido;
import cl.duoc.pedidos360.web.ReglaNegocioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas unitarias de los totales y de la maquina de estados del pedido. */
class PedidoDominioTest {

  private Pedido nuevoPedido() {
    return new Pedido(
        "PED-2026-00001",
        1L,
        "76123456-7",
        "Comercial Andes SpA",
        "oid-vendedor",
        "Vendedor Demo",
        "Av. Providencia 1234",
        null);
  }

  @Test
  @DisplayName("Calcula neto, IVA y total a partir del detalle")
  void calculaTotales() {
    Pedido pedido = nuevoPedido();
    pedido.agregarDetalle(new DetallePedido(1L, "NB-001", "Notebook", 2, new BigDecimal("100000")));
    pedido.agregarDetalle(new DetallePedido(2L, "MS-004", "Mouse", 3, new BigDecimal("10000")));

    assertThat(pedido.getNeto()).isEqualByComparingTo("230000.00");
    assertThat(pedido.getIva()).isEqualByComparingTo("43700.00");
    assertThat(pedido.getTotal()).isEqualByComparingTo("273700.00");
  }

  @Test
  @DisplayName("No permite confirmar un pedido sin lineas")
  void noConfirmaPedidoVacio() {
    assertThatThrownBy(() -> nuevoPedido().confirmar())
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("sin lineas");
  }

  @Test
  @DisplayName("Respeta las transiciones validas de la maquina de estados")
  void respetaTransiciones() {
    Pedido pedido = nuevoPedido();
    pedido.agregarDetalle(new DetallePedido(1L, "NB-001", "Notebook", 1, new BigDecimal("100000")));

    pedido.confirmar();
    assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);

    pedido.cambiarEstado(EstadoPedido.EN_PREPARACION);
    pedido.cambiarEstado(EstadoPedido.DESPACHADO);
    pedido.cambiarEstado(EstadoPedido.ENTREGADO);

    assertThatThrownBy(() -> pedido.cambiarEstado(EstadoPedido.ANULADO))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("No es posible pasar el pedido");
  }

  @Test
  @DisplayName("Un pedido confirmado ya no admite nuevas lineas")
  void bloqueaEdicionTrasConfirmar() {
    Pedido pedido = nuevoPedido();
    pedido.agregarDetalle(new DetallePedido(1L, "NB-001", "Notebook", 1, new BigDecimal("100000")));
    pedido.confirmar();

    assertThatThrownBy(
            () ->
                pedido.agregarDetalle(
                    new DetallePedido(2L, "MS-004", "Mouse", 1, new BigDecimal("10000"))))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("BORRADOR");
  }

  @Test
  @DisplayName("Solo los estados posteriores a la confirmacion reservan stock")
  void identificaEstadosQueReservanStock() {
    assertThat(EstadoPedido.BORRADOR.reservaStock()).isFalse();
    assertThat(EstadoPedido.ANULADO.reservaStock()).isFalse();
    assertThat(EstadoPedido.CONFIRMADO.reservaStock()).isTrue();
    assertThat(EstadoPedido.DESPACHADO.reservaStock()).isTrue();
  }
}
