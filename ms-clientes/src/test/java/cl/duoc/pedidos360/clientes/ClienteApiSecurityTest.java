package cl.duoc.pedidos360.clientes;

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

/** Pruebas de autorizacion por rol sobre el API de clientes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ClienteApiSecurityTest {

  @Autowired private MockMvc mockMvc;

  private static SimpleGrantedAuthority rol(String rol) {
    return new SimpleGrantedAuthority("ROLE_" + rol);
  }

  @Test
  @DisplayName("Sin token responde 401")
  void sinTokenRechaza() throws Exception {
    mockMvc.perform(get("/api/v1/clientes")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("El rol Pedidos.Cliente no puede listar la cartera completa")
  void clienteNoListaCartera() throws Exception {
    mockMvc
        .perform(get("/api/v1/clientes").with(jwt().authorities(rol("PEDIDOS_CLIENTE"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("El rol Pedidos.Vendedor lista y crea clientes")
  void vendedorGestionaClientes() throws Exception {
    mockMvc
        .perform(get("/api/v1/clientes").with(jwt().authorities(rol("PEDIDOS_VENDEDOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].rut").exists());

    mockMvc
        .perform(
            post("/api/v1/clientes")
                .with(jwt().authorities(rol("PEDIDOS_VENDEDOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rut":"11111111-1","razonSocial":"Cliente de prueba","email":"prueba@correo.cl",
                     "telefono":"+56900000000","segmento":"MINORISTA",
                     "direccion":{"calle":"Av. Siempre Viva","numero":"742","comuna":"Nunoa",
                                  "ciudad":"Santiago","region":"Metropolitana"}}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rut").value("11111111-1"))
        .andExpect(jsonPath("$.direccionFormateada").value("Av. Siempre Viva 742, Nunoa, Santiago"));
  }

  @Test
  @DisplayName("Un rut mal formado devuelve 400")
  void validaRut() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/clientes")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rut":"no-es-rut","razonSocial":"X","email":"correo-invalido","segmento":"MINORISTA",
                     "direccion":{"calle":"Calle","comuna":"Comuna","ciudad":"Ciudad"}}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores.rut").exists())
        .andExpect(jsonPath("$.errores.email").exists());
  }

  @Test
  @DisplayName("Mi-ficha responde 404 cuando el usuario del token no tiene ficha asociada")
  void miFichaSinVinculo() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/clientes/mi-ficha")
                .with(
                    jwt()
                        .jwt(builder -> builder.claim("oid", "oid-sin-ficha"))
                        .authorities(rol("PEDIDOS_CLIENTE"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
  }
}
