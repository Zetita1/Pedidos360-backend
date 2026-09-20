package cl.duoc.pedidos360.bff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.pedidos360.bff.client.DominioClient;
import cl.duoc.pedidos360.security.Pedidos360SecurityAutoConfiguration;
import cl.duoc.pedidos360.security.Pedidos360SecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Comprueba que el BFF valide el JWT igual que el JWT authorizer del API Gateway: firma con
 * la llave del IDaaS, issuer, audiencia, tenant, vigencia y autorizacion por rol.
 *
 * <p>Los tokens se firman en la propia prueba con un par de llaves RSA generado al vuelo, de
 * modo que no se requiere conexion al IDaaS real.
 */
@SpringBootTest(
    properties = {
      "pedidos360.security.issuer=https://pedidos360.ciamlogin.com/tenant-duoc/v2.0",
      "pedidos360.security.audiences=api://pedidos360-api",
      "pedidos360.security.tenant-id=tenant-duoc",
      "pedidos360.security.required-scopes=Pedidos.Read"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(BffTokenValidacionTest.DecoderConLlaveDePrueba.class)
class BffTokenValidacionTest {

  private static final String ISSUER = "https://pedidos360.ciamlogin.com/tenant-duoc/v2.0";
  private static final String AUDIENCIA = "api://pedidos360-api";
  private static final String TENANT = "tenant-duoc";

  private static final RSAKey LLAVE_IDAAS = generarLlave("llave-idaas");
  private static final RSAKey LLAVE_ATACANTE = generarLlave("llave-falsa");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private DominioClient dominioClient;

  /** Sustituye el JWK Set remoto por la llave publica de prueba, manteniendo los validadores. */
  @TestConfiguration
  static class DecoderConLlaveDePrueba {

    @Bean
    JwtDecoder jwtDecoder(Pedidos360SecurityProperties properties) throws JOSEException {
      RSAPublicKey publica = LLAVE_IDAAS.toRSAPublicKey();
      NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publica).build();
      decoder.setJwtValidator(Pedidos360SecurityAutoConfiguration.tokenValidator(properties));
      return decoder;
    }
  }

  @BeforeEach
  void simularMicroservicios() {
    JsonNode vacio = objectMapper.createArrayNode();
    given(dominioClient.pedidos(any(), any())).willReturn(ResponseEntity.ok(vacio));
    given(dominioClient.productos(any(), any())).willReturn(ResponseEntity.ok(vacio));
  }

  @Test
  @DisplayName("Sin token: 401 con cabecera WWW-Authenticate y Problem Detail")
  void rechazaPeticionSinToken() throws Exception {
    mockMvc
        .perform(get("/api/bff/tablero"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("invalid_token"));
  }

  @Test
  @DisplayName("Token con firma de otra llave: 401")
  void rechazaFirmaInvalida() throws Exception {
    String token = firmar(claimsValidos().build(), LLAVE_ATACANTE);

    mockMvc
        .perform(get("/api/bff/tablero").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Token vencido: 401")
  void rechazaTokenVencido() throws Exception {
    String token =
        firmar(
            claimsValidos()
                .issueTime(Date.from(Instant.now().minus(4, ChronoUnit.HOURS)))
                .expirationTime(Date.from(Instant.now().minus(3, ChronoUnit.HOURS)))
                .build(),
            LLAVE_IDAAS);

    mockMvc
        .perform(get("/api/bff/tablero").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Token emitido para otra audiencia: 401")
  void rechazaAudienciaIncorrecta() throws Exception {
    String token = firmar(claimsValidos().audience("api://otra-aplicacion").build(), LLAVE_IDAAS);

    mockMvc
        .perform(get("/api/bff/tablero").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Token emitido por otro issuer: 401")
  void rechazaIssuerIncorrecto() throws Exception {
    String token =
        firmar(
            claimsValidos().issuer("https://pedidos360.ciamlogin.com/otro-tenant/v2.0").build(),
            LLAVE_IDAAS);

    mockMvc
        .perform(get("/api/bff/tablero").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Token sin el scope minimo de la API: 401")
  void rechazaScopeInsuficiente() throws Exception {
    String token = firmar(claimsValidos().claim("scp", "Otro.Scope").build(), LLAVE_IDAAS);

    mockMvc
        .perform(get("/api/bff/tablero").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Token valido pero sin el rol exigido: 403")
  void rechazaRolInsuficiente() throws Exception {
    String token = firmar(claimsValidos().claim("roles", List.of("Pedidos.Cliente")).build(), LLAVE_IDAAS);

    mockMvc
        .perform(get("/api/bff/tablero").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Acceso denegado"));
  }

  @Test
  @DisplayName("Token valido con rol de gestion: 200")
  void aceptaTokenValido() throws Exception {
    String token = firmar(claimsValidos().build(), LLAVE_IDAAS);

    mockMvc
        .perform(get("/api/bff/tablero").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("El perfil expone roles, scopes y permisos leidos del token")
  void exponePerfilDesdeElToken() throws Exception {
    String token =
        firmar(
            claimsValidos()
                .claim("roles", List.of("Pedidos.Admin"))
                .claim("name", "Ana Perez")
                .claim("preferred_username", "ana.perez@duocuc.cl")
                .build(),
            LLAVE_IDAAS);

    mockMvc
        .perform(get("/api/bff/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value("Ana Perez"))
        .andExpect(jsonPath("$.email").value("ana.perez@duocuc.cl"))
        .andExpect(jsonPath("$.tenantId").value(TENANT))
        .andExpect(jsonPath("$.roles[0]").value("Pedidos.Admin"))
        .andExpect(jsonPath("$.scopes[0]").value("Pedidos.Read"))
        .andExpect(jsonPath("$.permisos.gestionarCatalogo").value(true))
        .andExpect(jsonPath("$.permisos.anularPedidos").value(true));
  }

  @Test
  @DisplayName("El health check queda publico para el balanceador de EC2")
  void healthEsPublico() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  private JWTClaimsSet.Builder claimsValidos() {
    return new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(AUDIENCIA)
        .subject("sub-usuario")
        .claim("oid", "oid-usuario")
        .claim("tid", TENANT)
        .claim("scp", "Pedidos.Read Pedidos.Write")
        .claim("roles", List.of("Pedidos.Vendedor"))
        .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
        .expirationTime(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
  }

  private String firmar(JWTClaimsSet claims, RSAKey llave) throws JOSEException {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(llave.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(llave));
    return jwt.serialize();
  }

  private static RSAKey generarLlave(String kid) {
    try {
      return new RSAKeyGenerator(2048).keyID(kid).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("No fue posible generar la llave de prueba", e);
    }
  }
}
