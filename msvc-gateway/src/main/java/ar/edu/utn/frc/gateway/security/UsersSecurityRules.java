package ar.edu.utn.frc.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.stereotype.Component;

@Component
public class UsersSecurityRules {

    public void configure(
            ServerHttpSecurity.AuthorizeExchangeSpec exchanges) {

        // Registro público.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/users/clientes",
                "/api/users/clientes/")
                .permitAll();

        // Perfil propio.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/users/me",
                "/api/users/me/")
                .authenticated();

        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/users/me",
                "/api/users/me/")
                .authenticated();

        // Exclusivo del canal entre servicios.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/users/clientes/{id}/habilitacion",
                "/api/users/clientes/{id}/habilitacion/")
                .denyAll();

        // Alta de administradores.
        exchanges.pathMatchers(HttpMethod.POST,
                "/api/users/administradores",
                "/api/users/administradores/")
                .hasRole("ADMIN");

        // Consulta de clientes.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/users/clientes",
                "/api/users/clientes/",
                "/api/users/clientes/{id}",
                "/api/users/clientes/{id}/")
                .hasRole("ADMIN");

        // Consulta de administradores.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/users/administradores",
                "/api/users/administradores/",
                "/api/users/administradores/{id}",
                "/api/users/administradores/{id}/")
                .hasRole("ADMIN");

        // Activación y desactivación de perfiles.
        exchanges.pathMatchers(HttpMethod.PATCH,
                "/api/users/{id}/estado",
                "/api/users/{id}/estado/")
                .hasRole("ADMIN");

        // Asignación de complejo.
        exchanges.pathMatchers(HttpMethod.PUT,
                "/api/users/administradores/{id}/complejo",
                "/api/users/administradores/{id}/complejo/")
                .hasRole("ADMIN");

        // Consulta del complejo asignado.
        exchanges.pathMatchers(HttpMethod.GET,
                "/api/users/administradores/{id}/complejo",
                "/api/users/administradores/{id}/complejo/")
                .hasRole("ADMIN");
    }
}