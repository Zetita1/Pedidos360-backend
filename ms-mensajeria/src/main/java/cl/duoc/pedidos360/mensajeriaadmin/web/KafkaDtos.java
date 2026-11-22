package cl.duoc.pedidos360.mensajeriaadmin.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Contratos de entrada y salida de la administracion de Kafka.
 *
 * <p>La entrada se valida antes de tocar el cluster: un nombre vacio o con caracteres que
 * Kafka no acepta se rechaza con 400 en el borde, sin abrir una conexion al broker.
 */
public final class KafkaDtos {

  private KafkaDtos() {}

  /** Caracteres que Kafka admite en el nombre de un topico o de un grupo. */
  public static final String PATRON_NOMBRE = "^[a-zA-Z0-9._-]+$";

  /**
   * Peticion para crear un topico.
   *
   * @param nombre nombre del topico
   * @param particiones unidades de paralelismo; define cuantos consumidores de un grupo
   *     pueden trabajar a la vez
   * @param replicas copias de cada particion; no puede superar el numero de brokers
   * @param configuraciones parametros opcionales como retention.ms o cleanup.policy
   */
  public record CrearTopicoRequest(
      @NotBlank @Size(max = 200) @Pattern(regexp = PATRON_NOMBRE) String nombre,
      @Positive @Max(50) Integer particiones,
      @Positive @Max(10) Integer replicas,
      Map<String, String> configuraciones) {

    /**
     * Particiones pedidas o el valor por omision del proyecto.
     *
     * @param porDefecto particiones a usar si la peticion no las trae
     * @return particiones a crear
     */
    public int particionesOPorDefecto(int porDefecto) {
      return particiones == null ? porDefecto : particiones;
    }

    /**
     * Replicas pedidas o el valor por omision del proyecto.
     *
     * @param porDefecto replicas a usar si la peticion no las trae
     * @return replicas a crear
     */
    public short replicasOPorDefecto(short porDefecto) {
      return replicas == null ? porDefecto : replicas.shortValue();
    }
  }

  /**
   * Peticion para cambiar la configuracion de un topico.
   *
   * @param configuraciones parametros a aplicar, por ejemplo {@code retention.ms}
   */
  public record ConfigurarTopicoRequest(
      @NotEmpty Map<@NotBlank String, @NotBlank String> configuraciones) {}

  /**
   * Peticion para ampliar las particiones de un topico.
   *
   * <p>Kafka solo permite subir: bajarlas reubicaria claves ya escritas y rompria el orden
   * garantizado por clave.
   *
   * @param particiones total de particiones que debe quedar
   */
  public record AmpliarParticionesRequest(@Positive @Max(50) int particiones) {}

  /**
   * Resumen de un topico.
   *
   * @param nombre nombre del topico
   * @param particiones cantidad de particiones
   * @param replicas replicas por particion
   * @param interno si es un topico interno de Kafka
   * @param delSistema si forma parte de la topologia que usa la aplicacion
   * @param replicasIncompletas particiones cuyo conjunto de replicas en sincronia esta
   *     incompleto; si no es cero, el cluster perdio redundancia
   * @param politicaLimpieza valor de cleanup.policy
   * @param retencionMs valor de retention.ms, nulo cuando el topico se compacta
   */
  public record TopicoResponse(
      String nombre,
      int particiones,
      int replicas,
      boolean interno,
      boolean delSistema,
      int replicasIncompletas,
      String politicaLimpieza,
      String retencionMs) {}

  /**
   * Estado de una particion.
   *
   * @param particion numero de particion
   * @param lider broker que atiende las lecturas y escrituras
   * @param replicas brokers que tienen copia
   * @param enSincronia brokers cuya copia esta al dia
   * @param primerOffset primer offset disponible; sube cuando la retencion borra segmentos
   * @param ultimoOffset offset del proximo registro a escribir
   * @param registros registros disponibles en la particion
   */
  public record ParticionResponse(
      int particion,
      Integer lider,
      List<Integer> replicas,
      List<Integer> enSincronia,
      long primerOffset,
      long ultimoOffset,
      long registros) {}

  /**
   * Detalle de un topico con sus particiones y configuracion efectiva.
   *
   * @param nombre nombre del topico
   * @param interno si es un topico interno de Kafka
   * @param delSistema si forma parte de la topologia de la aplicacion
   * @param particiones estado de cada particion
   * @param configuraciones configuracion no predeterminada del topico
   */
  public record TopicoDetalleResponse(
      String nombre,
      boolean interno,
      boolean delSistema,
      List<ParticionResponse> particiones,
      Map<String, String> configuraciones) {}

  /**
   * Resumen de un grupo de consumidores.
   *
   * @param grupo identificador del grupo
   * @param estado estado informado por el coordinador
   * @param miembros consumidores conectados
   * @param delSistema si es uno de los grupos de la aplicacion
   * @param lagTotal registros pendientes de consumir en todas sus particiones
   */
  public record GrupoResponse(
      String grupo, String estado, int miembros, boolean delSistema, long lagTotal) {}

  /**
   * Avance de un grupo en una particion.
   *
   * @param topico topico leido
   * @param particion particion leida
   * @param offsetActual hasta donde confirmo el grupo
   * @param ultimoOffset hasta donde escribio el productor
   * @param lag diferencia entre ambos: lo que queda por procesar
   * @param miembro consumidor que tiene asignada la particion
   */
  public record AvanceParticionResponse(
      String topico,
      int particion,
      long offsetActual,
      long ultimoOffset,
      long lag,
      String miembro) {}

  /**
   * Detalle de un grupo con el avance de cada particion.
   *
   * @param grupo identificador del grupo
   * @param estado estado informado por el coordinador
   * @param coordinador broker que coordina el grupo
   * @param estrategiaAsignacion como reparte las particiones entre miembros
   * @param delSistema si es uno de los grupos de la aplicacion
   * @param lagTotal registros pendientes en todas sus particiones
   * @param avance avance por particion
   */
  public record GrupoDetalleResponse(
      String grupo,
      String estado,
      String coordinador,
      String estrategiaAsignacion,
      boolean delSistema,
      long lagTotal,
      List<AvanceParticionResponse> avance) {}

  /**
   * Estado general del cluster.
   *
   * @param idCluster identificador del cluster
   * @param brokers brokers activos
   * @param controlador broker que actua de controlador
   * @param topicos topicos de negocio, sin contar los internos
   * @param particionesConReplicasIncompletas particiones que perdieron redundancia
   * @param grupos grupos de consumidores registrados
   */
  public record ClusterResponse(
      String idCluster,
      List<String> brokers,
      String controlador,
      int topicos,
      int particionesConReplicasIncompletas,
      int grupos) {}

  /**
   * Resultado de una operacion administrativa.
   *
   * @param operacion que se hizo
   * @param recurso sobre que se hizo
   * @param detalle descripcion legible del resultado
   */
  public record OperacionKafkaResponse(String operacion, String recurso, String detalle) {}
}
