package cl.duoc.pedidos360.analitica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Registro de cada evento ya procesado.
 *
 * <p>Cumple dos funciones a la vez. La primera es la idempotencia: Kafka garantiza entrega
 * <i>al menos</i> una vez, asi que ante un reequilibrio del grupo o un reinicio del
 * consumidor el mismo registro puede volver a llegar. Antes de procesar se consulta esta
 * tabla, y si el evento ya esta, se descarta sin volver a sumarlo a las metricas.
 *
 * <p>La segunda es la trazabilidad: guarda topico, particion y offset, de modo que cada
 * numero del tablero se puede seguir hasta el registro exacto del que salio.
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "evento_id", nullable = false, unique = true, length = 60)
  private String eventoId;

  @Column(nullable = false, length = 120)
  private String topico;

  @Column(nullable = false)
  private int particion;

  @Column(name = "desplazamiento", nullable = false)
  private long offset;

  @Column(length = 80)
  private String clave;

  /** Que hecho traia el registro, para poder leer el flujo sin abrir el cuerpo del mensaje. */
  @Column(length = 40)
  private String tipo;

  @Column(name = "procesado_en", nullable = false)
  private Instant procesadoEn = Instant.now();

  protected EventoProcesado() {}

  /**
   * @param eventoId identificador del evento
   * @param topico topico del que se leyo
   * @param particion particion de origen
   * @param offset posicion dentro de la particion
   * @param clave clave de particionado del registro
   * @param tipo hecho que traia el registro
   */
  public EventoProcesado(
      String eventoId, String topico, int particion, long offset, String clave, String tipo) {
    this.eventoId = eventoId;
    this.topico = topico;
    this.particion = particion;
    this.offset = offset;
    this.clave = clave;
    this.tipo = tipo;
  }

  public Long getId() {
    return id;
  }

  public String getEventoId() {
    return eventoId;
  }

  public String getTopico() {
    return topico;
  }

  public int getParticion() {
    return particion;
  }

  public long getOffset() {
    return offset;
  }

  public String getClave() {
    return clave;
  }

  public String getTipo() {
    return tipo;
  }

  public Instant getProcesadoEn() {
    return procesadoEn;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof EventoProcesado otro && id != null && id.equals(otro.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
