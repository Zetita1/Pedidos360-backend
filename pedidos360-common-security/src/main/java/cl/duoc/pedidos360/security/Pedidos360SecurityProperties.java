package cl.duoc.pedidos360.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del IDaaS (Microsoft Entra ID) usados para validar los JWT que llegan
 * desde el API Gateway o directamente desde el frontend Angular.
 *
 * <p>Se configuran bajo el prefijo {@code pedidos360.security} en {@code application.yml}.
 */
@ConfigurationProperties(prefix = "pedidos360.security")
public class Pedidos360SecurityProperties {

  /** Issuer esperado del token. Ej: https://login.microsoftonline.com/{tenantId}/v2.0 */
  private String issuer;

  /**
   * URL del JWK Set con las llaves publicas del IDaaS. Si no se indica se deriva del issuer,
   * evitando una llamada de discovery al momento de levantar el servicio.
   */
  private String jwkSetUri;

  /** Audiencias aceptadas. Ej: api://{clientId} y/o el propio {clientId}. */
  private List<String> audiences = new ArrayList<>();

  /** Tenant (claim {@code tid}) aceptado. Vacio = no se valida el tenant. */
  private String tenantId;

  /** Scopes delegados que el token debe incluir si o si (claim {@code scp}). */
  private List<String> requiredScopes = new ArrayList<>();

  /** Tolerancia de reloj al validar {@code exp} y {@code nbf}. */
  private long clockSkewSeconds = 60;

  /** Rutas que se exponen sin token (health checks, documentacion OpenAPI, etc.). */
  private List<String> publicPaths =
      new ArrayList<>(
          List.of(
              "/actuator/health",
              "/actuator/health/**",
              "/actuator/info",
              "/v3/api-docs",
              "/v3/api-docs/**",
              "/swagger-ui.html",
              "/swagger-ui/**"));

  /** Origenes permitidos por CORS (el frontend Angular). */
  private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:4200"));

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getJwkSetUri() {
    return jwkSetUri;
  }

  public void setJwkSetUri(String jwkSetUri) {
    this.jwkSetUri = jwkSetUri;
  }

  public List<String> getAudiences() {
    return audiences;
  }

  public void setAudiences(List<String> audiences) {
    this.audiences = audiences;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public List<String> getRequiredScopes() {
    return requiredScopes;
  }

  public void setRequiredScopes(List<String> requiredScopes) {
    this.requiredScopes = requiredScopes;
  }

  public long getClockSkewSeconds() {
    return clockSkewSeconds;
  }

  public void setClockSkewSeconds(long clockSkewSeconds) {
    this.clockSkewSeconds = clockSkewSeconds;
  }

  public List<String> getPublicPaths() {
    return publicPaths;
  }

  public void setPublicPaths(List<String> publicPaths) {
    this.publicPaths = publicPaths;
  }

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  /**
   * JWK Set efectivo: el configurado explicitamente o el derivado del issuer de Entra ID.
   *
   * @return url del JWK Set
   */
  public String resolveJwkSetUri() {
    if (jwkSetUri != null && !jwkSetUri.isBlank()) {
      return jwkSetUri;
    }
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalStateException(
          "Debe configurar pedidos360.security.issuer o pedidos360.security.jwk-set-uri");
    }
    String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    if (base.endsWith("/v2.0")) {
      base = base.substring(0, base.length() - "/v2.0".length());
    }
    return base + "/discovery/v2.0/keys";
  }
}
