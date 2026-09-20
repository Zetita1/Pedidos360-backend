package cl.duoc.pedidos360.pedidos.client;

/** Puerto hacia el microservicio de clientes. */
public interface ClientesClient {

  /**
   * Obtiene la ficha del cliente.
   *
   * @param clienteId identificador del cliente
   * @return datos del cliente
   */
  ClienteInfo obtener(Long clienteId);

  /** Vista reducida del cliente que necesita el pedido. */
  record ClienteInfo(
      Long id, String rut, String razonSocial, String direccionFormateada, boolean activo) {}
}
