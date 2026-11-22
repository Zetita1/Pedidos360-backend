package cl.duoc.pedidos360.analitica.service;

/**
 * De donde salio un registro: topico, particion, posicion y clave.
 *
 * <p>Existe para que los servicios de negocio puedan guardar la trazabilidad sin conocer las
 * clases de Kafka. El listener traduce la metadata del consumidor a este tipo, y de ahi hacia
 * dentro no entra nada de la infraestructura de mensajeria.
 *
 * @param topico topico del que se leyo el registro
 * @param particion particion de origen
 * @param offset posicion dentro de la particion
 * @param clave clave de particionado
 */
public record OrigenRegistro(String topico, int particion, long offset, String clave) {

  /**
   * Describe el origen como {@code topico-particion@offset}.
   *
   * @return ubicacion legible del registro
   */
  public String ubicacion() {
    return topico + "-" + particion + "@" + offset;
  }
}
