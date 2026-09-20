package cl.duoc.pedidos360.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuracion compartida de resource server. Cada microservicio (y el BFF) la hereda y queda
 * validando el JWT del IDaaS con las mismas reglas que aplica el API Gateway: firma, issuer,
 * audiencia, vigencia y tenant.
 */
@AutoConfiguration
@EnableConfigurationProperties(Pedidos360SecurityProperties.class)
@EnableMethodSecurity
public class Pedidos360SecurityAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(Pedidos360SecurityAutoConfiguration.class);

  /**
   * Cadena de validadores del token: vigencia (exp/nbf con tolerancia de reloj), issuer,
   * audiencia, tenant y scopes minimos.
   *
   * @param properties parametros del IDaaS
   * @return validador delegado
   */
  public static OAuth2TokenValidator<Jwt> tokenValidator(Pedidos360SecurityProperties properties) {
    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(new JwtTimestampValidator(Duration.ofSeconds(properties.getClockSkewSeconds())));

    if (properties.getIssuer() != null && !properties.getIssuer().isBlank()) {
      validators.add(new JwtIssuerValidator(properties.getIssuer()));
    }
    if (!properties.getAudiences().isEmpty()) {
      validators.add(new AudienceValidator(properties.getAudiences()));
    } else {
      log.warn(
          "pedidos360.security.audiences esta vacio: el recurso aceptaria tokens emitidos para "
              + "otras aplicaciones del mismo tenant");
    }
    if (properties.getTenantId() != null && !properties.getTenantId().isBlank()) {
      validators.add(new TenantValidator(properties.getTenantId()));
    }
    if (!properties.getRequiredScopes().isEmpty()) {
      validators.add(new RequiredScopeValidator(properties.getRequiredScopes()));
    }
    return new DelegatingOAuth2TokenValidator<>(validators);
  }

  /**
   * Decoder Nimbus que descarga las llaves publicas del IDaaS (JWKS) para verificar la firma
   * y encadena todas las validaciones de claims exigidas por el encargo.
   *
   * @param properties parametros del IDaaS
   * @return decoder con validadores encadenados
   */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder jwtDecoder(Pedidos360SecurityProperties properties) {
    String jwkSetUri = properties.resolveJwkSetUri();
    log.info("Resource server validando tokens con el JWK Set {}", jwkSetUri);

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(tokenValidator(properties));
    return decoder;
  }

  /**
   * Convierte los claims {@code roles} y {@code scp} del token en autoridades de Spring Security.
   *
   * @return converter de autenticacion
   */
  @Bean
  @ConditionalOnMissingBean(name = "entraIdJwtAuthenticationConverter")
  public Converter<Jwt, AbstractAuthenticationToken> entraIdJwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new EntraIdAuthoritiesConverter());
    converter.setPrincipalClaimName(EntraIdClaims.CLAIM_OID);
    return converter;
  }

  @Bean
  @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
  public AuthenticationEntryPoint pedidos360AuthenticationEntryPoint(ObjectMapper objectMapper) {
    return new Pedidos360AuthenticationEntryPoint(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(AccessDeniedHandler.class)
  public AccessDeniedHandler pedidos360AccessDeniedHandler(ObjectMapper objectMapper) {
    return new Pedidos360AccessDeniedHandler(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(CorsConfigurationSource.class)
  public CorsConfigurationSource corsConfigurationSource(Pedidos360SecurityProperties properties) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.getAllowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Correlation-Id"));
    config.setExposedHeaders(List.of("X-Correlation-Id"));
    config.setMaxAge(Duration.ofMinutes(30));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  /**
   * Cadena de filtros por defecto: API sin estado, todo protegido salvo las rutas publicas
   * declaradas, y verificacion del Bearer token en cada peticion.
   *
   * @param http builder de Spring Security
   * @param properties parametros del IDaaS
   * @param jwtAuthenticationConverter converter de claims a autoridades
   * @param entryPoint manejador de 401
   * @param accessDeniedHandler manejador de 403
   * @param corsConfigurationSource politica CORS para el frontend Angular
   * @return cadena de filtros
   * @throws Exception si la configuracion es invalida
   */
  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  public SecurityFilterChain pedidos360FilterChain(
      HttpSecurity http,
      Pedidos360SecurityProperties properties,
      Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
      AuthenticationEntryPoint entryPoint,
      AccessDeniedHandler accessDeniedHandler,
      CorsConfigurationSource corsConfigurationSource)
      throws Exception {

    String[] publicPaths = properties.getPublicPaths().toArray(String[]::new);

    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(publicPaths)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable());

    return http.build();
  }
}
