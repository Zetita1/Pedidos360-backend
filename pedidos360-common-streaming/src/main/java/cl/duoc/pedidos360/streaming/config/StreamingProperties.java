package cl.duoc.pedidos360.streaming.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de topicos, grupos de consumidores y politicas de retencion del streaming de
 * Pedidos360, en un unico lugar.
 *
 * <p>Se configuran bajo el prefijo {@code pedidos360.streaming} en {@code application.yml}.
 * La conexion al cluster (bootstrap servers, serializers, tamanos de lote) vive en
 * {@code spring.kafka}, que es el contrato del cliente; aqui viven las decisiones del
 * proyecto: que topicos existen, con cuantas particiones y replicas, y quien los consume.
 *
 * <p>Ninguna clase de negocio escribe el nombre de un topico ni de un grupo a mano: todas
 * los piden a este componente.
 */
@ConfigurationProperties(prefix = "pedidos360.streaming")
public class StreamingProperties {

  /**
   * Si la aplicacion declara los topicos al arrancar.
   *
   * <p>Se desactiva en el perfil de pruebas, donde no hay cluster al que declararlos.
   */
  private boolean declararTopicos = true;

  /** Particiones de cada topico de negocio. */
  private int particiones = 3;

  /**
   * Replicas de cada particion.
   *
   * <p>Con tres brokers y factor 3, cada particion tiene un lider y dos seguidores. Junto a
   * {@code min.insync.replicas=2} permite perder un broker sin perder datos y sin dejar de
   * escribir.
   */
  private short replicas = 3;

  /** Sufijo de los topicos de descarte (dead letter topic). */
  private String sufijoDlt = ".dlt";

  /** Intentos de procesamiento antes de derivar el registro al DLT. */
  private int maxIntentos = 3;

  /** Espera inicial en milisegundos entre reintentos; se duplica en cada intento. */
  private long esperaReintentoMs = 1000;

  /** Consumidores simultaneos por listener. Conviene igualarlo al numero de particiones. */
  private int consumidores = 3;

  /** Retencion del flujo de hechos de pedidos: siete dias. */
  private long retencionPedidosMs = 604_800_000L;

  /** Retencion de los topicos de descarte: catorce dias, porque se revisan a mano. */
  private long retencionDescarteMs = 1_209_600_000L;

  /** Replicas que deben confirmar una escritura para darla por hecha. */
  private short replicasEnSincronia = 2;

  /** Topicos por flujo de datos. La clave es el nombre logico del flujo. */
  private Map<String, String> topicos =
      new LinkedHashMap<>(
          Map.of(
              "pedidos", "pedidos360.pedidos.eventos",
              "stock", "pedidos360.inventario.stock"));

  /** Grupos de consumidores por caso de uso. La clave es el nombre logico del caso de uso. */
  private Map<String, String> grupos =
      new LinkedHashMap<>(
          Map.of(
              "analitica-ventas", "pedidos360-analitica-ventas",
              "proyeccion-stock", "pedidos360-proyeccion-stock",
              "archivo-pedidos", "pedidos360-archivo-pedidos"));

  /**
   * Nombre de un topico de negocio.
   *
   * @param flujo nombre logico del flujo, por ejemplo {@code pedidos}
   * @return nombre del topico configurado
   * @throws IllegalStateException si el flujo no esta configurado
   */
  public String topico(String flujo) {
    String topico = topicos.get(flujo);
    if (topico == null || topico.isBlank()) {
      throw new IllegalStateException(
          "Falta configurar pedidos360.streaming.topicos." + flujo + " en application.yml");
    }
    return topico;
  }

  /**
   * Nombre de un grupo de consumidores.
   *
   * @param casoDeUso nombre logico del caso de uso, por ejemplo {@code analitica-ventas}
   * @return nombre del grupo configurado
   * @throws IllegalStateException si el caso de uso no esta configurado
   */
  public String grupo(String casoDeUso) {
    String grupo = grupos.get(casoDeUso);
    if (grupo == null || grupo.isBlank()) {
      throw new IllegalStateException(
          "Falta configurar pedidos360.streaming.grupos." + casoDeUso + " en application.yml");
    }
    return grupo;
  }

  /**
   * Topico de descarte asociado a un topico de negocio.
   *
   * @param topico nombre del topico principal
   * @return nombre del DLT
   */
  public String dltDe(String topico) {
    return topico + sufijoDlt;
  }

  public boolean isDeclararTopicos() {
    return declararTopicos;
  }

  public void setDeclararTopicos(boolean declararTopicos) {
    this.declararTopicos = declararTopicos;
  }

  public int getParticiones() {
    return particiones;
  }

  public void setParticiones(int particiones) {
    this.particiones = particiones;
  }

  public short getReplicas() {
    return replicas;
  }

  public void setReplicas(short replicas) {
    this.replicas = replicas;
  }

  public String getSufijoDlt() {
    return sufijoDlt;
  }

  public void setSufijoDlt(String sufijoDlt) {
    this.sufijoDlt = sufijoDlt;
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

  public long getRetencionPedidosMs() {
    return retencionPedidosMs;
  }

  public void setRetencionPedidosMs(long retencionPedidosMs) {
    this.retencionPedidosMs = retencionPedidosMs;
  }

  public long getRetencionDescarteMs() {
    return retencionDescarteMs;
  }

  public void setRetencionDescarteMs(long retencionDescarteMs) {
    this.retencionDescarteMs = retencionDescarteMs;
  }

  public short getReplicasEnSincronia() {
    return replicasEnSincronia;
  }

  public void setReplicasEnSincronia(short replicasEnSincronia) {
    this.replicasEnSincronia = replicasEnSincronia;
  }

  public Map<String, String> getTopicos() {
    return topicos;
  }

  public void setTopicos(Map<String, String> topicos) {
    this.topicos = topicos;
  }

  public Map<String, String> getGrupos() {
    return grupos;
  }

  public void setGrupos(Map<String, String> grupos) {
    this.grupos = grupos;
  }
}
