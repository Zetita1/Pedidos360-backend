package cl.duoc.pedidos360.streaming.consumo;

/**
 * El registro llego, se pudo leer, pero no sirve: le falta un dato obligatorio o trae un
 * valor imposible.
 *
 * <p>Se distingue a proposito de un fallo transitorio. Reintentar esto no cambia nada
 * —el contenido va a ser el mismo en el segundo intento y en el tercero—, asi que el manejador
 * de errores la trata como no recuperable y manda el registro al topico de descarte de
 * inmediato, sin gastar reintentos ni frenar la particion.
 */
public class EventoStreamInvalidoException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * @param mensaje motivo por el que el registro no es procesable
   */
  public EventoStreamInvalidoException(String mensaje) {
    super(mensaje);
  }
}
