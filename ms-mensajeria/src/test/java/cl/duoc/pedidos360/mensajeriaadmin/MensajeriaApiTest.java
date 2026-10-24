package cl.duoc.pedidos360.mensajeriaadmin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
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
 * API del administrador de mensajeria: autorizacion por rol, validacion de la entrada y
 * proteccion de la topologia del sistema.
 *
 * <p>El broker se simula: estas pruebas verifican el contrato del API, no RabbitMQ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MensajeriaApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RabbitAdmin rabbitAdmin;

  private static SimpleGrantedAuthority rol(String rol) {
    return new SimpleGrantedAuthority("ROLE_" + rol);
  }

  @Test
  @DisplayName("Sin token responde 401")
  void sinTokenRechaza() throws Exception {
    mockMvc.perform(get("/api/v1/mensajeria/colas")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("El rol Pedidos.Cliente no administra colas")
  void clienteNoAdministra() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/mensajeria/colas")
                .with(jwt().authorities(rol("PEDIDOS_CLIENTE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"reportes.diarios\"}"))
        .andExpect(status().isForbidden());

    verify(rabbitAdmin, never()).declareQueue(any(Queue.class));
  }

  @Test
  @DisplayName("Una cola sin nombre se rechaza con 400 y mensaje claro")
  void colaSinNombreSeRechaza() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/mensajeria/colas")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"   \"}"))
        .andExpect(status().isBadRequest());

    verify(rabbitAdmin, never()).declareQueue(any(Queue.class));
  }

  @Test
  @DisplayName("Un nombre de cola con caracteres invalidos se rechaza con 400")
  void nombreInvalidoSeRechaza() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/mensajeria/colas")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"cola con espacios\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Un tipo de exchange inexistente se rechaza con 400")
  void tipoDeExchangeInvalidoSeRechaza() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/mensajeria/exchanges")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"reportes.exchange\",\"tipo\":\"inventado\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("El administrador crea una cola nueva y responde 201")
  void adminCreaCola() throws Exception {
    given(rabbitAdmin.getQueueInfo("reportes.diarios"))
        .willReturn(null)
        .willReturn(new QueueInformation("reportes.diarios", 0, 0));
    given(rabbitAdmin.declareQueue(any(Queue.class))).willReturn("reportes.diarios");

    mockMvc
        .perform(
            post("/api/v1/mensajeria/colas")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"reportes.diarios\",\"durable\":true,\"conDlq\":false}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nombre").value("reportes.diarios"))
        .andExpect(jsonPath("$.delSistema").value(false));

    verify(rabbitAdmin).declareQueue(any(Queue.class));
  }

  @Test
  @DisplayName("Crear una cola que ya existe responde 409")
  void colaDuplicadaResponde409() throws Exception {
    given(rabbitAdmin.getQueueInfo("reportes.diarios"))
        .willReturn(new QueueInformation("reportes.diarios", 0, 0));

    mockMvc
        .perform(
            post("/api/v1/mensajeria/colas")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"reportes.diarios\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Las colas de la topologia del sistema no se pueden eliminar")
  void noSeEliminaLaTopologiaDelSistema() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/mensajeria/colas/pedidos360.notificaciones")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN"))))
        .andExpect(status().isConflict());

    verify(rabbitAdmin, never()).deleteQueue(anyString(), anyBoolean(), anyBoolean());
  }

  @Test
  @DisplayName("Eliminar una cola que no existe responde 404")
  void colaInexistenteResponde404() throws Exception {
    given(rabbitAdmin.getQueueInfo("reportes.fantasma")).willReturn(null);

    mockMvc
        .perform(
            delete("/api/v1/mensajeria/colas/reportes.fantasma")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN"))))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("El administrador elimina una cola propia")
  void adminEliminaCola() throws Exception {
    given(rabbitAdmin.getQueueInfo("reportes.diarios"))
        .willReturn(new QueueInformation("reportes.diarios", 0, 0));

    mockMvc
        .perform(
            delete("/api/v1/mensajeria/colas/reportes.diarios")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operacion").value("eliminar-cola"));

    verify(rabbitAdmin).deleteQueue("reportes.diarios", false, false);
  }

  @Test
  @DisplayName("La topologia declarada se expone con sus colas, DLQ y routing keys")
  void exponeLaTopologia() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/mensajeria/topologia").with(jwt().authorities(rol("PEDIDOS_CLIENTE"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exchange").value("pedidos360.eventos"))
        .andExpect(jsonPath("$.exchangeDescarte").value("pedidos360.eventos.dlx"))
        .andExpect(jsonPath("$.exchangeComandos").value("pedidos360.comandos"))
        // Ordenados alfabeticamente: auditoria es el primer caso de uso y su comando la
        // primera ruta, porque viaja por el exchange direct.
        .andExpect(jsonPath("$.casos[0].cola").value("pedidos360.auditoria"))
        .andExpect(jsonPath("$.casos[0].dlq").value("pedidos360.auditoria.dlq"))
        .andExpect(jsonPath("$.casos.length()").value(3))
        .andExpect(jsonPath("$.rutas[0].routingKey").value("auditoria.pedido-anulado"));
  }

  @Test
  @DisplayName("Un binding sin routing key se rechaza con 400")
  void bindingSinRoutingKeySeRechaza() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/mensajeria/bindings")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cola\":\"reportes.diarios\",\"exchange\":\"pedidos360.eventos\"}"))
        .andExpect(status().isBadRequest());
  }
}
