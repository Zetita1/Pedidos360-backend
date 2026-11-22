package cl.duoc.pedidos360.analitica.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parametros del servicio de analitica. */
@ConfigurationProperties(prefix = "pedidos360.analitica")
public class AnaliticaProperties {

  /** Unidades a partir de las cuales el stock de un producto se considera critico. */
  private int umbralStockCritico = 10;

  /**
   * Zona horaria con la que se agrupan los hechos por dia.
   *
   * <p>Los eventos viajan en UTC, que es lo correcto para transmitirlos. Pero "las ventas de
   * hoy" se cuentan en la hora local del negocio: sin esta conversion, todo lo vendido
   * despues de las 21:00 en Chile aparecia en el dia siguiente.
   */
  private String zonaHoraria = "America/Santiago";

  /** Dias que abarca el tablero por defecto. */
  private int diasTablero = 7;

  /**
   * Zona horaria resuelta.
   *
   * @return zona con la que agrupar por dia
   */
  public ZoneId zona() {
    return ZoneId.of(zonaHoraria);
  }

  public int getUmbralStockCritico() {
    return umbralStockCritico;
  }

  public void setUmbralStockCritico(int umbralStockCritico) {
    this.umbralStockCritico = umbralStockCritico;
  }

  public String getZonaHoraria() {
    return zonaHoraria;
  }

  public void setZonaHoraria(String zonaHoraria) {
    this.zonaHoraria = zonaHoraria;
  }

  public int getDiasTablero() {
    return diasTablero;
  }

  public void setDiasTablero(int diasTablero) {
    this.diasTablero = diasTablero;
  }
}
