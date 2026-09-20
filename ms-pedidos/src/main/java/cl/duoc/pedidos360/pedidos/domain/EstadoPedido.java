package cl.duoc.pedidos360.pedidos.domain;

import java.util.Set;

/** Ciclo de vida de un pedido. */
public enum EstadoPedido {
  BORRADOR,
  CONFIRMADO,
  EN_PREPARACION,
  DESPACHADO,
  ENTREGADO,
  ANULADO;

  /**
   * Indica si es posible pasar del estado actual al destino.
   *
   * @param destino estado al que se quiere avanzar
   * @return {@code true} si la transicion es valida
   */
  public boolean puedeAvanzarA(EstadoPedido destino) {
    return switch (this) {
      case BORRADOR -> Set.of(CONFIRMADO, ANULADO).contains(destino);
      case CONFIRMADO -> Set.of(EN_PREPARACION, ANULADO).contains(destino);
      case EN_PREPARACION -> Set.of(DESPACHADO, ANULADO).contains(destino);
      case DESPACHADO -> Set.of(ENTREGADO).contains(destino);
      case ENTREGADO, ANULADO -> false;
    };
  }

  /**
   * Estados en los que el stock ya fue descontado del catalogo.
   *
   * @return {@code true} si el pedido reserva stock
   */
  public boolean reservaStock() {
    return this == CONFIRMADO || this == EN_PREPARACION || this == DESPACHADO || this == ENTREGADO;
  }
}
