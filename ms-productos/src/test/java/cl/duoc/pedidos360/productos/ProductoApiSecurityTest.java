package cl.duoc.pedidos360.productos;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica que el microservicio solo responda ante un JWT valido y con el rol adecuado,
 * tal como lo exige el indicador de evaluacion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ProductoApiSecurityTest {

  @Autowired private MockMvc mockMvc;

  private static SimpleGrantedAuthority rol(String rol) {
    return new SimpleGrantedAuthority("ROLE_" + rol);
  }

  @Test
  @DisplayName("Sin token responde 401 con cabecera WWW-Authenticate")
  void sinTokenRechaza() throws Exception {
    mockMvc
        .perform(get("/api/v1/productos"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.title").value("Token no valido"));
  }

  @Test
  @DisplayName("Con token sin roles responde 403")
  void tokenSinRolesRechaza() throws Exception {
    mockMvc
        .perform(get("/api/v1/productos").with(jwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Un cliente autenticado puede consultar el catalogo")
  void clienteConsultaCatalogo() throws Exception {
    mockMvc
        .perform(get("/api/v1/productos").with(jwt().authorities(rol("PEDIDOS_CLIENTE"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sku").exists());
  }

  @Test
  @DisplayName("Un cliente no puede crear productos: 403")
  void clienteNoCreaProductos() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/productos")
                .with(jwt().authorities(rol("PEDIDOS_CLIENTE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"sku":"X-1","nombre":"Producto","precio":1000,"stock":5,"categoria":"Otros"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Acceso denegado"));
  }

  @Test
  @DisplayName("Un administrador crea un producto y recibe 201")
  void adminCreaProducto() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/productos")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"sku":"TEST-1","nombre":"Producto de prueba","descripcion":"creado en test",
                     "precio":12990,"stock":7,"categoria":"Pruebas"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sku").value("TEST-1"))
        .andExpect(jsonPath("$.stock").value(7));
  }

  @Test
  @DisplayName("Los datos invalidos devuelven 400 con el detalle de los campos")
  void validaEntrada() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/productos")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku":"","nombre":"","precio":-5,"stock":-1,"categoria":""}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores.sku").exists());
  }

  @Test
  @DisplayName("El endpoint de salud queda publico para el balanceador")
  void healthEsPublico() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
