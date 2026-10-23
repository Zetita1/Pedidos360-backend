package cl.duoc.pedidos360.notificaciones;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.pedidos360.mensajeria.evento.PedidoCreadoEvento;
import cl.duoc.pedidos360.notificaciones.service.NotificacionService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** El API de consulta exige token del IDaaS igual que el resto de los microservicios. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificacionApiSecurityTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private NotificacionService servicio;

  private static SimpleGrantedAuthority rol(String rol) {
    return new SimpleGrantedAuthority("ROLE_" + rol);
  }

  @BeforeEach
  void generarNotificacion() {
    servicio.registrarRecepcion(
        PedidoCreadoEvento.de(
            "PED-2026-00200",
            20L,
            10L,
            "Comercial Andes SpA",
            "contacto@andes.cl",
            new BigDecimal("50000"),
            1));
  }

  @Test
  @DisplayName("Sin token responde 401")
  void sinTokenRechaza() throws Exception {
    mockMvc.perform(get("/api/v1/notificaciones")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Con token y rol valido devuelve las notificaciones generadas")
  void conTokenDevuelveNotificaciones() throws Exception {
    mockMvc
        .perform(get("/api/v1/notificaciones").with(jwt().authorities(rol("PEDIDOS_CLIENTE"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].pedidoNumero").value("PED-2026-00200"))
        .andExpect(jsonPath("$[0].tipo").value("RECEPCION"));
  }

  @Test
  @DisplayName("El ticket de un pedido sin documento responde 404")
  void documentoInexistenteResponde404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/notificaciones/documentos/pedido/PED-NO-EXISTE")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN"))))
        .andExpect(status().isNotFound());
  }
}
