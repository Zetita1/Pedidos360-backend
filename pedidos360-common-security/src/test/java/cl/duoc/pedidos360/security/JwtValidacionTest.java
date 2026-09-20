package cl.duoc.pedidos360.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Pruebas de las reglas de validacion del JWT emitido por el IDaaS. */
class JwtValidacionTest {

  private static final String TENANT = "11111111-2222-3333-4444-555555555555";
  private static final String ISSUER = "https://login.microsoftonline.com/" + TENANT + "/v2.0";
  private static final String API_AUDIENCE = "api://pedidos360-api";

  private Jwt.Builder tokenBase() {
    return Jwt.withTokenValue("token-de-prueba")
        .header("alg", "RS256")
        .header("kid", "llave-1")
        .issuer(ISSUER)
        .subject("sub-usuario")
        .claim(EntraIdClaims.CLAIM_OID, "oid-usuario")
        .claim(EntraIdClaims.CLAIM_TENANT, TENANT)
        .issuedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
  }

  @Test
  @DisplayName("Acepta el token cuando la audiencia corresponde a la API")
  void aceptaAudienciaCorrecta() {
    Jwt jwt = tokenBase().audience(List.of(API_AUDIENCE)).build();

    OAuth2TokenValidatorResult resultado =
        new AudienceValidator(List.of(API_AUDIENCE)).validate(jwt);

    assertThat(resultado.hasErrors()).isFalse();
  }

  @Test
  @DisplayName("Rechaza un token emitido para otra aplicacion del mismo tenant")
  void rechazaAudienciaAjena() {
    Jwt jwt = tokenBase().audience(List.of("api://otra-api")).build();

    OAuth2TokenValidatorResult resultado =
        new AudienceValidator(List.of(API_AUDIENCE)).validate(jwt);

    assertThat(resultado.hasErrors()).isTrue();
    assertThat(resultado.getErrors().iterator().next().getDescription())
        .contains("La audiencia del token no corresponde");
  }

  @Test
  @DisplayName("Rechaza un token emitido por un tenant distinto")
  void rechazaOtroTenant() {
    Jwt jwt =
        tokenBase()
            .audience(List.of(API_AUDIENCE))
            .claim(EntraIdClaims.CLAIM_TENANT, "99999999-9999-9999-9999-999999999999")
            .build();

    assertThat(new TenantValidator(TENANT).validate(jwt).hasErrors()).isTrue();
  }

  @Test
  @DisplayName("Exige los scopes minimos declarados por la API")
  void exigeScopesMinimos() {
    Jwt sinScope = tokenBase().audience(List.of(API_AUDIENCE)).build();
    Jwt conScope =
        tokenBase()
            .audience(List.of(API_AUDIENCE))
            .claim(EntraIdClaims.CLAIM_SCOPE, "Pedidos.Read Pedidos.Write")
            .build();

    RequiredScopeValidator validador = new RequiredScopeValidator(List.of("Pedidos.Read"));

    assertThat(validador.validate(sinScope).hasErrors()).isTrue();
    assertThat(validador.validate(conScope).hasErrors()).isFalse();
  }

  @Test
  @DisplayName("Convierte roles y scopes del token en autoridades de Spring Security")
  void convierteClaimsEnAutoridades() {
    Jwt jwt =
        tokenBase()
            .audience(List.of(API_AUDIENCE))
            .claim(EntraIdClaims.CLAIM_ROLES, List.of("Pedidos.Admin", "Pedidos.Vendedor"))
            .claim(EntraIdClaims.CLAIM_SCOPE, "Pedidos.Read Pedidos.Write")
            .build();

    List<GrantedAuthority> autoridades =
        List.copyOf(new EntraIdAuthoritiesConverter().convert(jwt));

    assertThat(AuthorityUtils.authorityListToSet(autoridades))
        .containsExactlyInAnyOrder(
            "ROLE_PEDIDOS_ADMIN",
            "ROLE_PEDIDOS_VENDEDOR",
            "SCOPE_Pedidos.Read",
            "SCOPE_Pedidos.Write");
  }

  @Test
  @DisplayName("La cadena completa rechaza un token vencido")
  void rechazaTokenVencido() {
    Pedidos360SecurityProperties props = propiedades();
    Jwt vencido =
        Jwt.withTokenValue("token-vencido")
            .header("alg", "RS256")
            .issuer(ISSUER)
            .subject("sub-usuario")
            .audience(List.of(API_AUDIENCE))
            .claim(EntraIdClaims.CLAIM_TENANT, TENANT)
            .claim(EntraIdClaims.CLAIM_SCOPE, "Pedidos.Read")
            .issuedAt(Instant.now().minus(3, ChronoUnit.HOURS))
            .expiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
            .build();

    assertThat(
            Pedidos360SecurityAutoConfiguration.tokenValidator(props).validate(vencido).hasErrors())
        .isTrue();
  }

  @Test
  @DisplayName("La cadena completa acepta un token bien formado")
  void aceptaTokenValido() {
    Jwt jwt =
        tokenBase()
            .audience(List.of(API_AUDIENCE))
            .claim(EntraIdClaims.CLAIM_SCOPE, "Pedidos.Read")
            .claims(claims -> claims.put(EntraIdClaims.CLAIM_NAME, "Estudiante Duoc"))
            .build();

    assertThat(
            Pedidos360SecurityAutoConfiguration.tokenValidator(propiedades())
                .validate(jwt)
                .hasErrors())
        .isFalse();
  }

  @Test
  @DisplayName("Deriva el JWK Set a partir del issuer de Entra ID")
  void derivaJwkSetUri() {
    Pedidos360SecurityProperties props = new Pedidos360SecurityProperties();
    props.setIssuer(ISSUER);

    assertThat(props.resolveJwkSetUri())
        .isEqualTo("https://login.microsoftonline.com/" + TENANT + "/discovery/v2.0/keys");
  }

  @Test
  @DisplayName("Deriva el JWK Set de un tenant externo (Entra External ID / CIAM)")
  void derivaJwkSetUriDeTenantExterno() {
    Pedidos360SecurityProperties props = new Pedidos360SecurityProperties();
    props.setIssuer("https://pedidos360.ciamlogin.com/" + TENANT + "/v2.0");

    assertThat(props.resolveJwkSetUri())
        .isEqualTo("https://pedidos360.ciamlogin.com/" + TENANT + "/discovery/v2.0/keys");
  }

  @Test
  @DisplayName("Un JWK Set declarado a mano tiene prioridad sobre el derivado")
  void respetaJwkSetExplicito() {
    Pedidos360SecurityProperties props = new Pedidos360SecurityProperties();
    props.setIssuer(ISSUER);
    props.setJwkSetUri("https://ejemplo.cl/llaves.json");

    assertThat(props.resolveJwkSetUri()).isEqualTo("https://ejemplo.cl/llaves.json");
  }

  @Test
  @DisplayName("Falla al iniciar si no hay issuer ni JWK Set configurado")
  void exigeConfiguracionDelIdaas() {
    assertThatThrownBy(() -> new Pedidos360SecurityProperties().resolveJwkSetUri())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("pedidos360.security.issuer");
  }

  @Test
  @DisplayName("Lee identidad y nombre desde los claims del token")
  void leeIdentidadDelToken() {
    Jwt jwt =
        tokenBase()
            .audience(List.of(API_AUDIENCE))
            .claims(claims -> claims.putAll(Map.of(EntraIdClaims.CLAIM_NAME, "Ana Perez")))
            .build();

    assertThat(EntraIdClaims.userId(jwt)).isEqualTo("oid-usuario");
    assertThat(EntraIdClaims.displayName(jwt)).isEqualTo("Ana Perez");
  }

  private Pedidos360SecurityProperties propiedades() {
    Pedidos360SecurityProperties props = new Pedidos360SecurityProperties();
    props.setIssuer(ISSUER);
    props.setAudiences(List.of(API_AUDIENCE));
    props.setTenantId(TENANT);
    props.setRequiredScopes(List.of("Pedidos.Read"));
    return props;
  }
}
