package cl.duoc.pedidos360.mensajeriaadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cl.duoc.pedidos360.mensajeriaadmin.config.AdminStreamingProperties;
import cl.duoc.pedidos360.mensajeriaadmin.service.KafkaAdminService;
import cl.duoc.pedidos360.streaming.config.StreamingProperties;
import cl.duoc.pedidos360.web.ReglaNegocioException;
import org.apache.kafka.clients.admin.Admin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proteccion de la topologia del sistema en el administrador de Kafka.
 *
 * <p>Lo que se verifica no es que Kafka responda, sino que el servicio se niegue <i>antes</i>
 * de hablarle al cluster: un topico borrado se lleva la historia con el, y ahi no hay vuelta
 * atras.
 */
class KafkaAdminServiceTest {

  private Admin admin;
  private StreamingProperties streaming;
  private AdminStreamingProperties propiedades;
  private KafkaAdminService service;

  @BeforeEach
  void prepararServicio() {
    admin = mock(Admin.class);
    streaming = new StreamingProperties();
    propiedades = new AdminStreamingProperties();
    service = new KafkaAdminService(admin, streaming, propiedades);
  }

  @Test
  @DisplayName("Los topicos del proyecto y sus DLT se reconocen como del sistema")
  void reconoceLosTopicosDelSistema() {
    assertThat(service.esTopicoDelSistema("pedidos360.pedidos.eventos")).isTrue();
    assertThat(service.esTopicoDelSistema("pedidos360.inventario.stock")).isTrue();
    assertThat(service.esTopicoDelSistema("pedidos360.pedidos.eventos.dlt")).isTrue();
    assertThat(service.esTopicoDelSistema("pedidos360.inventario.stock.dlt")).isTrue();
    assertThat(service.esTopicoDelSistema("pruebas.topico")).isFalse();
  }

  @Test
  @DisplayName("Los grupos configurados se reconocen como del sistema")
  void reconoceLosGruposDelSistema() {
    assertThat(service.esGrupoDelSistema("pedidos360-analitica-ventas")).isTrue();
    assertThat(service.esGrupoDelSistema("pedidos360-proyeccion-stock")).isTrue();
    assertThat(service.esGrupoDelSistema("pedidos360-archivo-pedidos")).isTrue();
    assertThat(service.esGrupoDelSistema("grupo-de-pruebas")).isFalse();
  }

  @Test
  @DisplayName("Borrar un topico del sistema se rechaza sin consultar al cluster")
  void noBorraTopicosDelSistema() {
    assertThatThrownBy(() -> service.eliminarTopico("pedidos360.pedidos.eventos"))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("protegido");

    verify(admin, never()).deleteTopics(anyCollection());
    verify(admin, never()).describeTopics(anyCollection());
  }

  @Test
  @DisplayName("Borrar el DLT de un topico del sistema tambien se rechaza")
  void noBorraElDltDelSistema() {
    assertThatThrownBy(() -> service.eliminarTopico("pedidos360.inventario.stock.dlt"))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("protegido");

    verify(admin, never()).deleteTopics(anyCollection());
  }

  @Test
  @DisplayName("Borrar un grupo de la aplicacion se rechaza para no perder su avance")
  void noBorraGruposDelSistema() {
    assertThatThrownBy(() -> service.eliminarGrupo("pedidos360-analitica-ventas"))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("avance");

    verify(admin, never()).deleteConsumerGroups(anyCollection());
  }

  @Test
  @DisplayName("Con la proteccion desactivada, el borrado si llega al cluster")
  void sinProteccionLlegaAlCluster() {
    propiedades.setProtegerTopologiaDelSistema(false);

    // Sin proteccion el servicio ya no se niega, asi que consulta el topico en el cluster.
    // El mock no devuelve nada util, de modo que la operacion falla, pero lo que importa es
    // que la decision dejo de ser del proyecto y paso a ser de Kafka.
    assertThatThrownBy(() -> service.eliminarTopico("pedidos360.pedidos.eventos"))
        .isInstanceOf(RuntimeException.class);

    verify(admin).describeTopics(anyCollection());
  }
}
