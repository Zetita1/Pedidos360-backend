package cl.duoc.pedidos360.web;

/** Se lanza cuando la entidad solicitada no existe en la base de datos. */
public class RecursoNoEncontradoException extends RuntimeException {

  public RecursoNoEncontradoException(String mensaje) {
    super(mensaje);
  }

  public RecursoNoEncontradoException(String entidad, Object id) {
    super("No existe " + entidad + " con id " + id);
  }
}
