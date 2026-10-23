package cl.duoc.pedidos360.mensajeria.consumo;

/**
 * Error <b>no recuperable</b>: el evento llego incompleto o inconsistente y reintentarlo daria
 * siempre el mismo resultado.
 *
 * <p>El gestor de consumo lo manda directo a la DLQ, sin gastar reintentos.
 */
public class EventoNoProcesableException extends RuntimeException {

  public EventoNoProcesableException(String mensaje) {
    super(mensaje);
  }

  public EventoNoProcesableException(String mensaje, Throwable causa) {
    super(mensaje, causa);
  }
}
