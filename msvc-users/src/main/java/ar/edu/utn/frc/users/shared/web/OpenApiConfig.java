package ar.edu.utn.frc.users.shared.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI usersOpenApi() {
        return new OpenAPI().info(new Info().title("API Users").version("1.0.0")
                .description("La cuenta se registra en Keycloak. Esta API completa el perfil de negocio. "
                        + "Correo y contraseña permanecen en Keycloak. Las operaciones se ejecutan por el gateway."))
                .addServersItem(new Server().url("/").description("Gateway que sirve Swagger UI"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
