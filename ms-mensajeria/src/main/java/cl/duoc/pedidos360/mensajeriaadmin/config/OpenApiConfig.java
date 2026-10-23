package cl.duoc.pedidos360.mensajeriaadmin.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacion OpenAPI del administrador de mensajeria. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Pedidos360 - API de Mensajeria")
                .version("1.0.0")
                .description("Administracion de colas, exchanges y bindings de RabbitMQ")
                .contact(new Contact().name("Equipo Pedidos360 - DSY1107")))
        .addSecurityItem(new SecurityRequirement().addList("entra-id"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "entra-id",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "Access token JWT emitido por Microsoft Entra ID para la audiencia de la API")));
  }
}
