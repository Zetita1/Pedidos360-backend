package cl.duoc.pedidos360.mensajeriaadmin.service;

import cl.duoc.pedidos360.mensajeriaadmin.config.AdminStreamingProperties;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.AmpliarParticionesRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.AvanceParticionResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.ClusterResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.ConfigurarTopicoRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.CrearTopicoRequest;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.GrupoDetalleResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.GrupoResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.ParticionResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.TopicoDetalleResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.TopicoResponse;
import cl.duoc.pedidos360.streaming.config.StreamingProperties;
import cl.duoc.pedidos360.web.RecursoNoEncontradoException;
import cl.duoc.pedidos360.web.ReglaNegocioException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Administracion de la plataforma Kafka: topicos, configuraciones y grupos de consumidores.
 *
 * <p>Toda la conversacion con el cluster pasa por aqui. Los controladores reciben la peticion
 * y devuelven la respuesta, pero no conocen {@code Admin} ni las excepciones del cliente de
 * Kafka: este servicio las traduce a los errores del API.
 *
 * <p>La topologia que usa la aplicacion esta protegida. Borrar el topico de pedidos desde una
 * pantalla dejaria a la analitica sin flujo y, a diferencia de una cola vacia, con el topico
 * se va tambien la historia: no hay forma de reconstruirla.
 */
@Service
public class KafkaAdminService {

  private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

  /** Configuraciones que se muestran de un topico; el resto son valores por omision. */
  private static final Set<String> CONFIGS_RELEVANTES =
      Set.of(
          "cleanup.policy",
          "retention.ms",
          "retention.bytes",
          "min.insync.replicas",
          "segment.ms",
          "min.cleanable.dirty.ratio",
          "delete.retention.ms",
          "max.message.bytes",
          "compression.type");

  private final Admin admin;
  private final StreamingProperties streaming;
  private final AdminStreamingProperties propiedades;

  public KafkaAdminService(
      Admin admin, StreamingProperties streaming, AdminStreamingProperties propiedades) {
    this.admin = admin;
    this.streaming = streaming;
    this.propiedades = propiedades;
  }

  /**
   * Estado general del cluster.
   *
   * @return brokers, controlador y conteos
   */
  public ClusterResponse cluster() {
    DescribeClusterResult descripcion = admin.describeCluster();
    Collection<Node> nodos = esperar(descripcion.nodes());
    Node controlador = esperar(descripcion.controller());
    List<TopicoResponse> topicos = listarTopicos(false);
    int incompletas = topicos.stream().mapToInt(TopicoResponse::replicasIncompletas).sum();
    return new ClusterResponse(
        esperar(descripcion.clusterId()),
        nodos.stream().sorted(Comparator.comparingInt(Node::id)).map(KafkaAdminService::nombre).toList(),
        controlador == null ? "desconocido" : nombre(controlador),
        topicos.size(),
        incompletas,
        listarGrupos().size());
  }

  /**
   * Lista los topicos del cluster.
   *
   * @param incluirInternos si se incluyen los topicos internos de Kafka
   * @return topicos con sus particiones, replicas y politica
   */
  public List<TopicoResponse> listarTopicos(boolean incluirInternos) {
    Set<String> nombres =
        new LinkedHashSet<>(
            esperar(admin.listTopics(new ListTopicsOptions().listInternal(incluirInternos)).names()));
    if (nombres.isEmpty()) {
      return List.of();
    }
    Map<String, TopicDescription> descripciones =
        esperar(admin.describeTopics(nombres).allTopicNames());
    Map<String, Map<String, String>> configuraciones = configuracionesDe(nombres);

    List<TopicoResponse> respuesta = new ArrayList<>();
    for (TopicDescription descripcion : descripciones.values()) {
      Map<String, String> config =
          configuraciones.getOrDefault(descripcion.name(), Map.of());
      respuesta.add(
          new TopicoResponse(
              descripcion.name(),
              descripcion.partitions().size(),
              replicasDe(descripcion),
              descripcion.isInternal(),
              esTopicoDelSistema(descripcion.name()),
              particionesSinReplicar(descripcion),
              config.getOrDefault("cleanup.policy", "delete"),
              config.get("retention.ms")));
    }
    respuesta.sort(Comparator.comparing(TopicoResponse::nombre));
    return respuesta;
  }

  /**
   * Detalle de un topico, particion por particion.
   *
   * @param nombre nombre del topico
   * @return particiones con lider, replicas, sincronia y offsets
   */
  public TopicoDetalleResponse obtenerTopico(String nombre) {
    TopicDescription descripcion = describir(nombre);
    List<TopicPartition> particiones =
        descripcion.partitions().stream()
            .map(info -> new TopicPartition(nombre, info.partition()))
            .toList();

    Map<TopicPartition, ListOffsetsResultInfo> primeros = offsets(particiones, OffsetSpec.earliest());
    Map<TopicPartition, ListOffsetsResultInfo> ultimos = offsets(particiones, OffsetSpec.latest());

    List<ParticionResponse> detalle = new ArrayList<>();
    for (TopicPartitionInfo info : descripcion.partitions()) {
      TopicPartition clave = new TopicPartition(nombre, info.partition());
      long primero = offsetDe(primeros, clave);
      long ultimo = offsetDe(ultimos, clave);
      detalle.add(
          new ParticionResponse(
              info.partition(),
              info.leader() == null ? null : info.leader().id(),
              info.replicas().stream().map(Node::id).toList(),
              info.isr().stream().map(Node::id).toList(),
              primero,
              ultimo,
              Math.max(ultimo - primero, 0)));
    }
    detalle.sort(Comparator.comparingInt(ParticionResponse::particion));
    return new TopicoDetalleResponse(
        nombre,
        descripcion.isInternal(),
        esTopicoDelSistema(nombre),
        detalle,
        configuracionesDe(Set.of(nombre)).getOrDefault(nombre, Map.of()));
  }

  /**
   * Crea un topico.
   *
   * <p>Si la peticion no indica particiones ni replicas, se usan las del proyecto: tres y
   * tres, que es lo que permite paralelizar en tres consumidores y sobrevivir a la caida de
   * un broker.
   *
   * @param peticion datos del topico
   * @return detalle del topico creado
   */
  public TopicoDetalleResponse crearTopico(CrearTopicoRequest peticion) {
    String nombre = peticion.nombre().trim();
    int particiones = peticion.particionesOPorDefecto(streaming.getParticiones());
    short replicas = peticion.replicasOPorDefecto(streaming.getReplicas());

    int brokers = esperar(admin.describeCluster().nodes()).size();
    if (replicas > brokers) {
      throw new ReglaNegocioException(
          "No se puede pedir "
              + replicas
              + " replicas: el cluster tiene "
              + brokers
              + " brokers y cada replica vive en uno distinto");
    }

    NewTopic nuevo = new NewTopic(nombre, particiones, replicas);
    if (peticion.configuraciones() != null && !peticion.configuraciones().isEmpty()) {
      nuevo.configs(new HashMap<>(peticion.configuraciones()));
    }
    try {
      admin.createTopics(List.of(nuevo)).all().get(propiedades.getTimeoutSegundos(), TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof TopicExistsException) {
        throw new ReglaNegocioException("El topico " + nombre + " ya existe");
      }
      throw new ReglaNegocioException(
          "No se pudo crear el topico " + nombre + ": " + motivo(e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ReglaNegocioException("La creacion del topico " + nombre + " se interrumpio");
    } catch (TimeoutException e) {
      throw new ReglaNegocioException("El cluster no respondio al crear el topico " + nombre);
    }
    log.info("Topico {} creado con {} particiones y {} replicas", nombre, particiones, replicas);
    return obtenerTopico(nombre);
  }

  /**
   * Cambia la configuracion de un topico.
   *
   * <p>Se usa alteracion incremental: solo se tocan los parametros enviados y el resto queda
   * como estaba. Una alteracion completa reemplazaria toda la configuracion, y lo que no
   * viniera en la peticion volveria silenciosamente a su valor por omision.
   *
   * @param nombre nombre del topico
   * @param peticion configuraciones a aplicar
   * @return detalle del topico con la configuracion vigente
   */
  public TopicoDetalleResponse configurarTopico(String nombre, ConfigurarTopicoRequest peticion) {
    describir(nombre);
    ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
    List<AlterConfigOp> operaciones =
        peticion.configuraciones().entrySet().stream()
            .map(
                entrada ->
                    new AlterConfigOp(
                        new ConfigEntry(entrada.getKey(), entrada.getValue()),
                        AlterConfigOp.OpType.SET))
            .toList();
    try {
      admin.incrementalAlterConfigs(Map.of(recurso, operaciones))
          .all()
          .get(propiedades.getTimeoutSegundos(), TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      throw new ReglaNegocioException(
          "No se pudo configurar el topico " + nombre + ": " + motivo(e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ReglaNegocioException("La configuracion del topico " + nombre + " se interrumpio");
    } catch (TimeoutException e) {
      throw new ReglaNegocioException("El cluster no respondio al configurar " + nombre);
    }
    log.info("Topico {} reconfigurado: {}", nombre, peticion.configuraciones().keySet());
    return obtenerTopico(nombre);
  }

  /**
   * Amplia las particiones de un topico.
   *
   * <p>Kafka solo admite aumentar. Reducirlas cambiaria la particion que le toca a cada
   * clave, y los registros ya escritos quedarian separados de los nuevos de la misma clave:
   * se perderia la garantia de orden que es la razon de elegir esa clave.
   *
   * @param nombre nombre del topico
   * @param peticion total de particiones que debe quedar
   * @return detalle del topico ampliado
   */
  public TopicoDetalleResponse ampliarParticiones(
      String nombre, AmpliarParticionesRequest peticion) {
    TopicDescription descripcion = describir(nombre);
    int actuales = descripcion.partitions().size();
    if (peticion.particiones() <= actuales) {
      throw new ReglaNegocioException(
          "El topico "
              + nombre
              + " ya tiene "
              + actuales
              + " particiones; Kafka solo permite aumentarlas");
    }
    try {
      admin.createPartitions(Map.of(nombre, NewPartitions.increaseTo(peticion.particiones())))
          .all()
          .get(propiedades.getTimeoutSegundos(), TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof InvalidPartitionsException) {
        throw new ReglaNegocioException(
            "El cluster rechazo el numero de particiones: " + motivo(e));
      }
      throw new ReglaNegocioException("No se pudo ampliar " + nombre + ": " + motivo(e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ReglaNegocioException("La ampliacion de " + nombre + " se interrumpio");
    } catch (TimeoutException e) {
      throw new ReglaNegocioException("El cluster no respondio al ampliar " + nombre);
    }
    log.info("Topico {} ampliado de {} a {} particiones", nombre, actuales, peticion.particiones());
    return obtenerTopico(nombre);
  }

  /**
   * Elimina un topico que no pertenezca a la topologia del sistema.
   *
   * @param nombre nombre del topico
   */
  public void eliminarTopico(String nombre) {
    // La proteccion se evalua antes de consultar el cluster: si la respuesta va a ser que no,
    // no hay razon para abrir una conexion al broker.
    if (propiedades.isProtegerTopologiaDelSistema() && esTopicoDelSistema(nombre)) {
      throw new ReglaNegocioException(
          "El topico "
              + nombre
              + " forma parte de la topologia del sistema y esta protegido contra borrado");
    }
    TopicDescription descripcion = describir(nombre);
    if (descripcion.isInternal()) {
      throw new ReglaNegocioException(
          "El topico " + nombre + " es interno de Kafka y no se puede eliminar");
    }
    try {
      admin.deleteTopics(List.of(nombre))
          .all()
          .get(propiedades.getTimeoutSegundos(), TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      throw new ReglaNegocioException("No se pudo eliminar " + nombre + ": " + motivo(e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ReglaNegocioException("El borrado de " + nombre + " se interrumpio");
    } catch (TimeoutException e) {
      throw new ReglaNegocioException("El cluster no respondio al eliminar " + nombre);
    }
    log.info("Topico {} eliminado", nombre);
  }

  /**
   * Lista los grupos de consumidores con su lag total.
   *
   * @return grupos registrados en el cluster
   */
  public List<GrupoResponse> listarGrupos() {
    Collection<ConsumerGroupListing> listados = esperar(admin.listConsumerGroups().valid());
    if (listados.isEmpty()) {
      return List.of();
    }
    List<String> ids = listados.stream().map(ConsumerGroupListing::groupId).toList();
    Map<String, ConsumerGroupDescription> descripciones =
        esperar(admin.describeConsumerGroups(ids).all());

    List<GrupoResponse> respuesta = new ArrayList<>();
    for (String id : ids) {
      ConsumerGroupDescription descripcion = descripciones.get(id);
      respuesta.add(
          new GrupoResponse(
              id,
              descripcion == null ? "desconocido" : descripcion.state().toString(),
              descripcion == null ? 0 : descripcion.members().size(),
              esGrupoDelSistema(id),
              lagTotalDe(id)));
    }
    respuesta.sort(Comparator.comparing(GrupoResponse::grupo));
    return respuesta;
  }

  /**
   * Detalle de un grupo: quien tiene cada particion y cuanto le falta por leer.
   *
   * <p>Es la vista que permite anticipar problemas. Un lag que sube sostenidamente dice que
   * el consumo no alcanza al ingreso; un lag alto concentrado en una sola particion dice que
   * la clave esta mal elegida y el reparto quedo desbalanceado; una particion sin miembro
   * asignado dice que el grupo tiene menos consumidores que particiones.
   *
   * @param grupo identificador del grupo
   * @return avance por particion
   */
  public GrupoDetalleResponse obtenerGrupo(String grupo) {
    Map<String, ConsumerGroupDescription> descripciones =
        esperar(admin.describeConsumerGroups(List.of(grupo)).all());
    ConsumerGroupDescription descripcion = descripciones.get(grupo);
    if (descripcion == null || descripcion.members().isEmpty() && offsetsDe(grupo).isEmpty()) {
      throw new RecursoNoEncontradoException("El grupo " + grupo + " no existe en el cluster");
    }

    Map<TopicPartition, String> duenos = new HashMap<>();
    for (MemberDescription miembro : descripcion.members()) {
      for (TopicPartition particion : miembro.assignment().topicPartitions()) {
        duenos.put(particion, miembro.consumerId());
      }
    }

    Map<TopicPartition, OffsetAndMetadata> confirmados = offsetsDe(grupo);
    Map<TopicPartition, ListOffsetsResultInfo> finales =
        offsets(List.copyOf(confirmados.keySet()), OffsetSpec.latest());

    List<AvanceParticionResponse> avance = new ArrayList<>();
    long lagTotal = 0;
    for (Map.Entry<TopicPartition, OffsetAndMetadata> entrada : confirmados.entrySet()) {
      TopicPartition particion = entrada.getKey();
      long actual = entrada.getValue() == null ? 0 : entrada.getValue().offset();
      long ultimo = offsetDe(finales, particion);
      long lag = Math.max(ultimo - actual, 0);
      lagTotal += lag;
      avance.add(
          new AvanceParticionResponse(
              particion.topic(),
              particion.partition(),
              actual,
              ultimo,
              lag,
              duenos.getOrDefault(particion, "sin asignar")));
    }
    avance.sort(
        Comparator.comparing(AvanceParticionResponse::topico)
            .thenComparingInt(AvanceParticionResponse::particion));

    Node coordinador = descripcion.coordinator();
    return new GrupoDetalleResponse(
        grupo,
        descripcion.state().toString(),
        coordinador == null ? "desconocido" : nombre(coordinador),
        descripcion.partitionAssignor(),
        esGrupoDelSistema(grupo),
        lagTotal,
        avance);
  }

  /**
   * Elimina un grupo de consumidores que no pertenezca a la aplicacion.
   *
   * <p>Borrar un grupo borra su avance. Al volver a conectarse, sus consumidores empiezan
   * donde diga {@code auto.offset.reset}: desde el principio, reprocesando todo, o desde el
   * final, saltandose lo pendiente.
   *
   * @param grupo identificador del grupo
   */
  public void eliminarGrupo(String grupo) {
    if (propiedades.isProtegerTopologiaDelSistema() && esGrupoDelSistema(grupo)) {
      throw new ReglaNegocioException(
          "El grupo "
              + grupo
              + " es uno de los que usa la aplicacion y esta protegido: borrarlo perderia su"
              + " avance");
    }
    try {
      admin.deleteConsumerGroups(List.of(grupo))
          .all()
          .get(propiedades.getTimeoutSegundos(), TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      throw new ReglaNegocioException("No se pudo eliminar el grupo " + grupo + ": " + motivo(e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ReglaNegocioException("El borrado del grupo " + grupo + " se interrumpio");
    } catch (TimeoutException e) {
      throw new ReglaNegocioException("El cluster no respondio al eliminar el grupo " + grupo);
    }
    log.info("Grupo de consumidores {} eliminado", grupo);
  }

  // ------------------------------------------------------------------ apoyo

  /**
   * Si el topico forma parte de la topologia que usa la aplicacion.
   *
   * @param nombre nombre del topico
   * @return true cuando es uno de los topicos del proyecto o su DLT
   */
  public boolean esTopicoDelSistema(String nombre) {
    for (String topico : streaming.getTopicos().values()) {
      if (topico.equals(nombre) || streaming.dltDe(topico).equals(nombre)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Si el grupo es uno de los que usa la aplicacion.
   *
   * @param grupo identificador del grupo
   * @return true cuando el grupo esta configurado en el proyecto
   */
  public boolean esGrupoDelSistema(String grupo) {
    return streaming.getGrupos().containsValue(grupo);
  }

  private TopicDescription describir(String nombre) {
    try {
      return admin.describeTopics(List.of(nombre))
          .allTopicNames()
          .get(propiedades.getTimeoutSegundos(), TimeUnit.SECONDS)
          .get(nombre);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof UnknownTopicOrPartitionException) {
        throw new RecursoNoEncontradoException("El topico " + nombre + " no existe en el cluster");
      }
      throw new ReglaNegocioException("No se pudo consultar " + nombre + ": " + motivo(e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ReglaNegocioException("La consulta de " + nombre + " se interrumpio");
    } catch (TimeoutException e) {
      throw new ReglaNegocioException("El cluster no respondio al consultar " + nombre);
    }
  }

  /**
   * Configuracion no predeterminada de un conjunto de topicos.
   *
   * @param nombres topicos a consultar
   * @return configuraciones relevantes por topico
   */
  private Map<String, Map<String, String>> configuracionesDe(Set<String> nombres) {
    List<ConfigResource> recursos =
        nombres.stream()
            .map(nombre -> new ConfigResource(ConfigResource.Type.TOPIC, nombre))
            .toList();
    Map<ConfigResource, Config> configuraciones;
    try {
      configuraciones =
          admin.describeConfigs(recursos)
              .all()
              .get(propiedades.getTimeoutSegundos(), TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      log.debug("No fue posible leer la configuracion de los topicos: {}", e.getMessage());
      return Map.of();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Map.of();
    }

    Map<String, Map<String, String>> respuesta = new LinkedHashMap<>();
    configuraciones.forEach(
        (recurso, config) -> {
          Map<String, String> relevantes = new LinkedHashMap<>();
          for (ConfigEntry entrada : config.entries()) {
            // Se muestran los parametros que el proyecto ajusta; el resto son los valores con
            // los que viene el broker y solo harian ruido.
            if (CONFIGS_RELEVANTES.contains(entrada.name())
                && entrada.source() != ConfigEntry.ConfigSource.DEFAULT_CONFIG) {
              relevantes.put(entrada.name(), entrada.value());
            }
          }
          respuesta.put(recurso.name(), relevantes);
        });
    return respuesta;
  }

  private Map<TopicPartition, OffsetAndMetadata> offsetsDe(String grupo) {
    try {
      return admin.listConsumerGroupOffsets(grupo)
          .partitionsToOffsetAndMetadata()
          .get(propiedades.getTimeoutSegundos(), TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      log.debug("No fue posible leer los offsets del grupo {}: {}", grupo, e.getMessage());
      return Map.of();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Map.of();
    }
  }

  private long lagTotalDe(String grupo) {
    Map<TopicPartition, OffsetAndMetadata> confirmados = offsetsDe(grupo);
    if (confirmados.isEmpty()) {
      return 0;
    }
    Map<TopicPartition, ListOffsetsResultInfo> finales =
        offsets(List.copyOf(confirmados.keySet()), OffsetSpec.latest());
    long total = 0;
    for (Map.Entry<TopicPartition, OffsetAndMetadata> entrada : confirmados.entrySet()) {
      long actual = entrada.getValue() == null ? 0 : entrada.getValue().offset();
      total += Math.max(offsetDe(finales, entrada.getKey()) - actual, 0);
    }
    return total;
  }

  private Map<TopicPartition, ListOffsetsResultInfo> offsets(
      List<TopicPartition> particiones, OffsetSpec especificacion) {
    if (particiones.isEmpty()) {
      return Map.of();
    }
    Map<TopicPartition, OffsetSpec> peticion = new LinkedHashMap<>();
    particiones.forEach(particion -> peticion.put(particion, especificacion));
    try {
      return admin.listOffsets(peticion)
          .all()
          .get(propiedades.getTimeoutSegundos(), TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      log.debug("No fue posible leer los offsets: {}", e.getMessage());
      return Map.of();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Map.of();
    }
  }

  private static long offsetDe(
      Map<TopicPartition, ListOffsetsResultInfo> offsets, TopicPartition particion) {
    ListOffsetsResultInfo info = offsets.get(particion);
    return info == null ? 0 : info.offset();
  }

  private static int replicasDe(TopicDescription descripcion) {
    return descripcion.partitions().stream()
        .mapToInt(info -> info.replicas().size())
        .max()
        .orElse(0);
  }

  /**
   * Particiones cuyo conjunto de replicas en sincronia esta incompleto.
   *
   * <p>Es la metrica que avisa que el cluster perdio redundancia: las particiones siguen
   * atendiendo, pero ya no tienen todas sus copias al dia.
   *
   * @param descripcion descripcion del topico
   * @return cantidad de particiones con replicas atrasadas
   */
  private static int particionesSinReplicar(TopicDescription descripcion) {
    return (int)
        descripcion.partitions().stream()
            .filter(info -> info.isr().size() < info.replicas().size())
            .count();
  }

  private static String nombre(Node nodo) {
    return nodo.id() + " (" + nodo.host() + ":" + nodo.port() + ")";
  }

  private <T> T esperar(KafkaFuture<T> futuro) {
    try {
      return futuro.get(propiedades.getTimeoutSegundos(), TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      throw new ReglaNegocioException("El cluster de Kafka respondio con error: " + motivo(e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ReglaNegocioException("La consulta al cluster se interrumpio");
    } catch (TimeoutException e) {
      throw new ReglaNegocioException(
          "El cluster de Kafka no respondio en "
              + propiedades.getTimeoutSegundos()
              + " segundos");
    }
  }

  private static String motivo(Exception e) {
    Throwable causa = e.getCause() == null ? e : e.getCause();
    return causa.getMessage() == null ? causa.getClass().getSimpleName() : causa.getMessage();
  }
}
