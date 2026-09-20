package cl.duoc.pedidos360.pedidos.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacion OpenAPI del servicio, declarando el esquema Bearer del IDaaS. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Pedidos360 - API de Pedidos")
                .version("1.0.0")
                .description("Pedidos, detalle y cambios de estado")
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
