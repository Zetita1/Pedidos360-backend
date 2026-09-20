package cl.duoc.pedidos360.web;

/** Se lanza cuando la peticion es sintacticamente valida pero viola una regla del dominio. */
public class ReglaNegocioException extends RuntimeException {

  public ReglaNegocioException(String mensaje) {
    super(mensaje);
  }
}
