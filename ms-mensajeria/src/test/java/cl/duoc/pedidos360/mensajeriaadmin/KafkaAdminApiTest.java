package cl.duoc.pedidos360.mensajeriaadmin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.GrupoResponse;
import cl.duoc.pedidos360.mensajeriaadmin.web.KafkaDtos.TopicoResponse;
import java.util.List;
import org.apache.kafka.clients.admin.Admin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API de administracion de Kafka: autorizacion por rol, validacion de la entrada y proteccion
 * de la topologia del sistema.
 *
 * <p>El cluster se simula. Estas pruebas verifican el contrato del API y las reglas del
 * proyecto, no el comportamiento de Kafka.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KafkaAdminApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RabbitAdmin rabbitAdmin;

  @MockitoBean private Admin admin;

  @MockitoBean private cl.duoc.pedidos360.mensajeriaadmin.service.KafkaAdminService kafkaAdmin;

  private static SimpleGrantedAuthority rol(String rol) {
    return new SimpleGrantedAuthority("ROLE_" + rol);
  }

  @Test
  @DisplayName("Sin token no se listan los topicos")
  void sinTokenRechaza() throws Exception {
    mockMvc.perform(get("/api/v1/streaming/topicos")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("El rol Pedidos.Cliente no administra topicos")
  void clienteNoAdministra() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/streaming/topicos")
                .with(jwt().authorities(rol("PEDIDOS_CLIENTE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"pruebas.topico\",\"particiones\":3,\"replicas\":3}"))
        .andExpect(status().isForbidden());

    verify(kafkaAdmin, never()).crearTopico(any());
  }

  @Test
  @DisplayName("El administrador ve los topicos con sus particiones y replicas")
  void administradorListaTopicos() throws Exception {
    given(kafkaAdmin.listarTopicos(false))
        .willReturn(
            List.of(
                new TopicoResponse(
                    "pedidos360.pedidos.eventos", 3, 3, false, true, 0, "delete", "604800000"),
                new TopicoResponse(
                    "pedidos360.inventario.stock", 3, 3, false, true, 0, "compact", null)));

    mockMvc
        .perform(get("/api/v1/streaming/topicos").with(jwt().authorities(rol("PEDIDOS_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].particiones").value(3))
        .andExpect(jsonPath("$[0].replicas").value(3))
        .andExpect(jsonPath("$[0].politicaLimpieza").value("delete"))
        .andExpect(jsonPath("$[1].politicaLimpieza").value("compact"));
  }

  @Test
  @DisplayName("Un nombre de topico vacio se rechaza con 400 sin tocar el cluster")
  void nombreVacioSeRechaza() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/streaming/topicos")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"  \",\"particiones\":3,\"replicas\":3}"))
        .andExpect(status().isBadRequest());

    verify(kafkaAdmin, never()).crearTopico(any());
  }

  @Test
  @DisplayName("Un nombre con caracteres que Kafka no acepta se rechaza con 400")
  void nombreInvalidoSeRechaza() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/streaming/topicos")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"topico con espacios\",\"particiones\":3}"))
        .andExpect(status().isBadRequest());

    verify(kafkaAdmin, never()).crearTopico(any());
  }

  @Test
  @DisplayName("Pedir mas particiones de las permitidas se rechaza con 400")
  void particionesExcesivasSeRechazan() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/streaming/topicos")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"pruebas.topico\",\"particiones\":500}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("El lag de un grupo se publica junto a su estado y sus miembros")
  void grupoPublicaSuLag() throws Exception {
    given(kafkaAdmin.listarGrupos())
        .willReturn(
            List.of(new GrupoResponse("pedidos360-analitica-ventas", "STABLE", 3, true, 12L)));

    mockMvc
        .perform(get("/api/v1/streaming/grupos").with(jwt().authorities(rol("PEDIDOS_VENDEDOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].grupo").value("pedidos360-analitica-ventas"))
        .andExpect(jsonPath("$[0].miembros").value(3))
        .andExpect(jsonPath("$[0].lagTotal").value(12))
        .andExpect(jsonPath("$[0].delSistema").value(true));
  }

  @Test
  @DisplayName("El rol Pedidos.Cliente no borra grupos de consumidores")
  void clienteNoBorraGrupos() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/streaming/grupos/pedidos360-analitica-ventas")
                .with(jwt().authorities(rol("PEDIDOS_CLIENTE"))))
        .andExpect(status().isForbidden());

    verify(admin, never()).deleteConsumerGroups(anyCollection());
  }
}
