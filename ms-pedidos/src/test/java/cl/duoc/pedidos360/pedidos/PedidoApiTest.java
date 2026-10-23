package cl.duoc.pedidos360.pedidos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.pedidos360.pedidos.client.CatalogoClient;
import cl.duoc.pedidos360.pedidos.client.CatalogoClient.ProductoInfo;
import cl.duoc.pedidos360.mensajeria.publicacion.PublicadorEventos;
import cl.duoc.pedidos360.pedidos.client.ClientesClient;
import cl.duoc.pedidos360.pedidos.client.ClientesClient.ClienteInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de extremo a extremo del API de pedidos con los microservicios de dominio
 * simulados: cubren autorizacion por rol, creacion, confirmacion y anulacion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PedidoApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private CatalogoClient catalogoClient;
  @MockitoBean private ClientesClient clientesClient;

  /** La publicacion de eventos se simula: las pruebas no dependen de un broker levantado. */
  @MockitoBean private PublicadorEventos publicadorEventos;

  private static SimpleGrantedAuthority rol(String rol) {
    return new SimpleGrantedAuthority("ROLE_" + rol);
  }

  @BeforeEach
  void prepararDependencias() {
    given(clientesClient.obtener(1L))
        .willReturn(
            new ClienteInfo(
                1L,
                "76123456-7",
                "Comercial Andes SpA",
                "contacto@comercialandes.cl",
                "Av. Providencia 1234, Santiago",
                true));
    given(catalogoClient.obtener(1L))
        .willReturn(new ProductoInfo(1L, "NB-001", "Notebook Pro 14", new BigDecimal("100000"), 10, true));
    given(catalogoClient.obtener(2L))
        .willReturn(new ProductoInfo(2L, "MS-004", "Mouse inalambrico", new BigDecimal("10000"), 2, true));
  }

  @Test
  @DisplayName("Sin token responde 401")
  void sinTokenRechaza() throws Exception {
    mockMvc.perform(get("/api/v1/pedidos")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("El rol Pedidos.Cliente no puede registrar pedidos")
  void clienteNoRegistraPedidos() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .with(jwt().authorities(rol("PEDIDOS_CLIENTE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"clienteId":1,"lineas":[{"productoId":1,"cantidad":1}]}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("El vendedor registra un pedido con precios y totales del catalogo")
  void vendedorRegistraPedido() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .with(
                    jwt()
                        .jwt(builder -> builder.claim("oid", "oid-vendedor").claim("name", "Vendedor Demo"))
                        .authorities(rol("PEDIDOS_VENDEDOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clienteId":1,"observacion":"Entregar en recepcion",
                     "lineas":[{"productoId":1,"cantidad":2},{"productoId":2,"cantidad":1}]}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estado").value("BORRADOR"))
        .andExpect(jsonPath("$.clienteRut").value("76123456-7"))
        .andExpect(jsonPath("$.registradoPor").value("Vendedor Demo"))
        .andExpect(jsonPath("$.neto").value(210000.00))
        .andExpect(jsonPath("$.iva").value(39900.00))
        .andExpect(jsonPath("$.total").value(249900.00))
        .andExpect(jsonPath("$.lineas.length()").value(2))
        .andExpect(jsonPath("$.direccionDespacho").value("Av. Providencia 1234, Santiago"));
  }

  @Test
  @DisplayName("Rechaza el pedido cuando el catalogo no tiene stock suficiente")
  void rechazaSinStock() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .with(jwt().authorities(rol("PEDIDOS_VENDEDOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"clienteId":1,"lineas":[{"productoId":2,"cantidad":99}]}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Regla de negocio no cumplida"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Stock insuficiente")));
  }

  @Test
  @DisplayName("Confirmar descuenta el stock y anular lo repone")
  void confirmaYAnula() throws Exception {
    Long pedidoId = crearPedido();

    mockMvc
        .perform(post("/api/v1/pedidos/{id}/confirmar", pedidoId)
            .with(jwt().authorities(rol("PEDIDOS_VENDEDOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

    verify(catalogoClient).descontarStock(eq(1L), eq(2), anyString());

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/pedidos/{id}/anular", pedidoId)
                .param("motivo", "Solicitud del cliente")
                .with(jwt().authorities(rol("PEDIDOS_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ANULADO"));

    verify(catalogoClient).reponerStock(eq(1L), eq(2), anyString());
  }

  @Test
  @DisplayName("Solo el rol Pedidos.Admin puede anular")
  void soloAdminAnula() throws Exception {
    Long pedidoId = crearPedido();

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/pedidos/{id}/anular", pedidoId)
                .with(jwt().authorities(rol("PEDIDOS_VENDEDOR"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Rechaza una transicion de estado invalida")
  void rechazaTransicionInvalida() throws Exception {
    Long pedidoId = crearPedido();

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/pedidos/{id}/estado", pedidoId)
                .with(jwt().authorities(rol("PEDIDOS_VENDEDOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"estado":"ENTREGADO","motivo":"salto de estados"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("El listado filtra por estado y trae el detalle de cada pedido")
  void listaPedidosConDetalle() throws Exception {
    crearPedido();

    mockMvc
        .perform(get("/api/v1/pedidos").with(jwt().authorities(rol("PEDIDOS_VENDEDOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].numero").exists())
        .andExpect(jsonPath("$[0].lineas.length()").value(org.hamcrest.Matchers.greaterThan(0)));

    mockMvc
        .perform(
            get("/api/v1/pedidos")
                .param("estado", "ENTREGADO")
                .with(jwt().authorities(rol("PEDIDOS_VENDEDOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Mis pedidos devuelve solo los del usuario del token")
  void listaSoloLosPropios() throws Exception {
    crearPedido();

    mockMvc
        .perform(
            get("/api/v1/pedidos/mis-pedidos")
                .with(
                    jwt()
                        .jwt(builder -> builder.claim("oid", "oid-vendedor"))
                        .authorities(rol("PEDIDOS_VENDEDOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    mockMvc
        .perform(
            get("/api/v1/pedidos/mis-pedidos")
                .with(
                    jwt()
                        .jwt(builder -> builder.claim("oid", "oid-de-otra-persona"))
                        .authorities(rol("PEDIDOS_CLIENTE"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("El resumen por estado alimenta el tablero del frontend")
  void entregaResumen() throws Exception {
    mockMvc
        .perform(get("/api/v1/pedidos/resumen").with(jwt().authorities(rol("PEDIDOS_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6));
  }

  private Long crearPedido() throws Exception {
    String cuerpo =
        mockMvc
            .perform(
                post("/api/v1/pedidos")
                    .with(
                        jwt()
                            .jwt(builder -> builder.claim("oid", "oid-vendedor"))
                            .authorities(rol("PEDIDOS_VENDEDOR")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"clienteId":1,"lineas":[{"productoId":1,"cantidad":2}]}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode json = objectMapper.readTree(cuerpo);
    return json.get("id").asLong();
  }

  @Test
  @DisplayName("Valida que el pedido traiga al menos una linea")
  void validaLineas() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .with(jwt().authorities(rol("PEDIDOS_VENDEDOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"clienteId":1,"lineas":[]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores.lineas").exists());
  }

  @Test
  @DisplayName("Propaga 404 cuando el cliente no existe en ms-clientes")
  void clienteInexistente() throws Exception {
    given(clientesClient.obtener(any()))
        .willThrow(new cl.duoc.pedidos360.web.RecursoNoEncontradoException("cliente", 99L));

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .with(jwt().authorities(rol("PEDIDOS_VENDEDOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"clienteId":99,"lineas":[{"productoId":1,"cantidad":1}]}
                    """))
        .andExpect(status().isNotFound());

    verify(catalogoClient, org.mockito.Mockito.never()).descontarStock(any(), anyInt(), anyString());
  }
}
