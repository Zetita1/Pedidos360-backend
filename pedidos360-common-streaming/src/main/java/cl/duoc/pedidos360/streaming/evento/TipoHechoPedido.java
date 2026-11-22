package cl.duoc.pedidos360.streaming.evento;

/**
 * Hecho de negocio que origino un registro en el flujo de pedidos.
 *
 * <p>Viaja como un campo del evento y no como un topico distinto: los cuatro hechos son del
 * mismo pedido y deben leerse en orden, lo que solo se garantiza dentro de una particion del
 * mismo topico. Separarlos en cuatro topicos haria imposible saber si la anulacion ocurrio
 * antes o despues de la confirmacion.
 */
public enum TipoHechoPedido {

  /** Se registro un pedido nuevo en estado borrador. */
  CREADO,

  /** El pedido se confirmo y reservo stock. */
  CONFIRMADO,

  /** El pedido avanzo en la maquina de estados. */
  ESTADO_CAMBIADO,

  /** El pedido se anulo; si tenia stock reservado, se repuso. */
  ANULADO
}
