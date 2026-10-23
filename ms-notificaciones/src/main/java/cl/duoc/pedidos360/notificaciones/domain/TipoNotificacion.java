package cl.duoc.pedidos360.notificaciones.domain;

/** Motivo por el que se genero el aviso al cliente. */
public enum TipoNotificacion {

  /** El pedido quedo registrado en estado BORRADOR. */
  RECEPCION,

  /** El pedido fue confirmado y pasa a preparacion. */
  CONFIRMACION,

  /** El pedido cambio de estado (despacho, entrega, anulacion). */
  SEGUIMIENTO
}
