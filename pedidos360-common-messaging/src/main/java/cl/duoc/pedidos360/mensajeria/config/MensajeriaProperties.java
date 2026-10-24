package cl.duoc.pedidos360.mensajeria.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchanges, colas, routing keys y politica de reintentos de toda la mensajeria
 * de Pedidos360, en un unico lugar.
 *
 * <p>Se configuran bajo el prefijo {@code pedidos360.mensajeria} en {@code application.yml};
 * ninguna clase de negocio escribe un nombre de cola o de exchange a mano: todas piden estos
 * valores a este componente.
 */
@ConfigurationProperties(prefix = "pedidos360.mensajeria")
public class MensajeriaProperties {

  /** Exchange de tipo topic por donde viajan los eventos de dominio. */
  private String exchange = "pedidos360.eventos";

  /**
   * Exchange de tipo direct para los comandos dirigidos a un unico consumidor.
   *
   * <p>Se usa topic cuando un hecho puede interesarle a varios (un pedido creado), y direct
   * cuando la orden tiene un destinatario exacto (registrar una operacion en auditoria).
   */
  private String exchangeComandos = "pedidos360.comandos";

  /** Exchange de descarte: recibe lo que las colas rechazan y lo deriva a su DLQ. */
  private String exchangeDescarte = "pedidos360.eventos.dlx";

  /** Sufijo de las colas de mensajes muertos. */
  private String sufijoDlq = ".dlq";

  /** Sufijo de las colas de espera entre reintentos. */
  private String sufijoReintento = ".retry";

  /** Intentos de procesamiento antes de enviar el mensaje a la DLQ. */
  private int maxIntentos = 3;

  /** Espera en milisegundos dentro de la cola de reintento antes de volver a la cola principal. */
  private long esperaReintentoMs = 5000;

  /** Consumidores simultaneos por listener. */
  private int consumidores = 2;

  /** Routing keys por evento de dominio. La clave es el nombre logico del evento. */
  private Map<String, String> rutas =
      new LinkedHashMap<>(
          Map.of(
              "pedido-creado", "pedido.creado",
              "pedido-confirmado", "pedido.confirmado",
              "pedido-estado-cambiado", "pedido.estado-cambiado",
              "pedido-anulado", "auditoria.pedido-anulado"));

  /** Colas por caso de uso. La clave es el nombre logico del caso de uso. */
  private Map<String, String> colas =
      new LinkedHashMap<>(
          Map.of(
              "notificaciones", "pedidos360.notificaciones",
              "documentos", "pedidos360.documentos",
              "auditoria", "pedidos360.auditoria"));

  /**
   * Routing key de un evento.
   *
   * @param evento nombre logico del evento, por ejemplo {@code pedido-creado}
   * @return routing key configurada
   * @throws IllegalStateException si el evento no esta configurado
   */
  public String ruta(String evento) {
    String ruta = rutas.get(evento);
    if (ruta == null || ruta.isBlank()) {
      throw new IllegalStateException(
          "Falta configurar pedidos360.mensajeria.rutas." + evento + " en application.yml");
    }
    return ruta;
  }

  /**
   * Nombre de la cola de un caso de uso.
   *
   * @param casoDeUso nombre logico del caso de uso, por ejemplo {@code notificaciones}
   * @return nombre de la cola configurada
   * @throws IllegalStateException si el caso de uso no esta configurado
   */
  public String cola(String casoDeUso) {
    String cola = colas.get(casoDeUso);
    if (cola == null || cola.isBlank()) {
      throw new IllegalStateException(
          "Falta configurar pedidos360.mensajeria.colas." + casoDeUso + " en application.yml");
    }
    return cola;
  }

  /**
   * Cola de mensajes muertos asociada a una cola.
   *
   * @param cola nombre de la cola principal
   * @return nombre de la DLQ
   */
  public String dlqDe(String cola) {
    return cola + sufijoDlq;
  }

  /**
   * Cola de espera entre reintentos asociada a una cola.
   *
   * @param cola nombre de la cola principal
   * @return nombre de la cola de reintento
   */
  public String reintentoDe(String cola) {
    return cola + sufijoReintento;
  }

  public String getExchange() {
    return exchange;
  }

  public void setExchange(String exchange) {
    this.exchange = exchange;
  }

  public String getExchangeComandos() {
    return exchangeComandos;
  }

  public void setExchangeComandos(String exchangeComandos) {
    this.exchangeComandos = exchangeComandos;
  }

  /**
   * Exchange por el que sale un evento segun su tipo.
   *
   * @param tipo tipo de exchange declarado por el evento
   * @return nombre del exchange configurado
   */
  public String exchangeDe(TipoExchange tipo) {
    return tipo == TipoExchange.COMANDOS ? exchangeComandos : exchange;
  }

  public String getExchangeDescarte() {
    return exchangeDescarte;
  }

  public void setExchangeDescarte(String exchangeDescarte) {
    this.exchangeDescarte = exchangeDescarte;
  }

  public String getSufijoDlq() {
    return sufijoDlq;
  }

  public void setSufijoDlq(String sufijoDlq) {
    this.sufijoDlq = sufijoDlq;
  }

  public String getSufijoReintento() {
    return sufijoReintento;
  }

  public void setSufijoReintento(String sufijoReintento) {
    this.sufijoReintento = sufijoReintento;
  }

  public int getMaxIntentos() {
    return maxIntentos;
  }

  public void setMaxIntentos(int maxIntentos) {
    this.maxIntentos = maxIntentos;
  }

  public long getEsperaReintentoMs() {
    return esperaReintentoMs;
  }

  public void setEsperaReintentoMs(long esperaReintentoMs) {
    this.esperaReintentoMs = esperaReintentoMs;
  }

  public int getConsumidores() {
    return consumidores;
  }

  public void setConsumidores(int consumidores) {
    this.consumidores = consumidores;
  }

  public Map<String, String> getRutas() {
    return rutas;
  }

  public void setRutas(Map<String, String> rutas) {
    this.rutas = rutas;
  }

  public Map<String, String> getColas() {
    return colas;
  }

  public void setColas(Map<String, String> colas) {
    this.colas = colas;
  }
}
