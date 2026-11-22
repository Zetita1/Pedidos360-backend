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
 * Aviso generado automaticamente cuando el stock de un producto cae al umbral critico.
 *
 * <p>No la crea ningun usuario ni ninguna pantalla: la dispara la llegada de un registro al
 * topico de stock. Es el proceso derivado del streaming —el sistema reacciona al hecho en vez
 * de esperar que alguien revise una pantalla.
 */
@Entity
@Table(name = "alerta_stock")
public class AlertaStock {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "producto_id", nullable = false)
  private Long productoId;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(length = 180)
  private String nombre;

  /** Stock que provoco el aviso. */
  @Column(nullable = false)
  private Integer stock;

  /** Umbral vigente cuando se genero. */
  @Column(nullable = false)
  private Integer umbral;

  @Column(nullable = false, length = 240)
  private String mensaje;

  /** Evento que lo origino, para poder rastrearlo hasta el registro del topico. */
  @Column(name = "evento_id", nullable = false, length = 60)
  private String eventoId;

  @Column(name = "generada_en", nullable = false)
  private Instant generadaEn = Instant.now();

  protected AlertaStock() {}

  /**
   * @param productoId identificador del producto
   * @param sku codigo del producto
   * @param nombre nombre del producto
   * @param stock stock que provoco el aviso
   * @param umbral umbral configurado
   * @param eventoId registro del topico que lo origino
   */
  public AlertaStock(
      Long productoId, String sku, String nombre, Integer stock, Integer umbral, String eventoId) {
    this.productoId = productoId;
    this.sku = sku;
    this.nombre = nombre;
    this.stock = stock;
    this.umbral = umbral;
    this.eventoId = eventoId;
    this.mensaje =
        stock == 0
            ? "El producto " + sku + " quedo sin stock"
            : "El producto " + sku + " bajo a " + stock + " unidades (umbral " + umbral + ")";
  }

  public Long getId() {
    return id;
  }

  public Long getProductoId() {
    return productoId;
  }

  public String getSku() {
    return sku;
  }

  public String getNombre() {
    return nombre;
  }

  public Integer getStock() {
    return stock;
  }

  public Integer getUmbral() {
    return umbral;
  }

  public String getMensaje() {
    return mensaje;
  }

  public String getEventoId() {
    return eventoId;
  }

  public Instant getGeneradaEn() {
    return generadaEn;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof AlertaStock otra && id != null && id.equals(otra.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
