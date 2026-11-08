package ar.edu.utn.frc.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            UsersSecurityRules usersRules,
            SpacesSecurityRules spacesRules,
            BookingsSecurityRules bookingsRules) {

        var converter = new JwtAuthenticationConverter();

        // Convertir los roles de Keycloak en autoridades de Spring Security.
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            Object roles = realm == null ? null : realm.get("roles");

            if (!(roles instanceof List<?> values)) {
                return List.of();
            }

            return values.stream()
                    .filter(String.class::isInstance)
                    .<GrantedAuthority>map(
                            role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        });

        return http
                // API sin sesión: autenticación mediante JWT.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(
                        NoOpServerSecurityContextRepository.getInstance())

                .authorizeExchange(exchanges -> {

                        // Salud del Gateway.
                        exchanges.pathMatchers(
                                "/actuator/health",
                                "/actuator/health/**")
                                .permitAll();

                        // Documentación pública.
                        exchanges.pathMatchers(HttpMethod.GET,
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/webjars/swagger-ui/**",
                                "/v3/api-docs/swagger-config",
                                "/docs/users")
                                .permitAll();

                        // Reglas de acceso del microservicio de usuarios.
                        usersRules.configure(exchanges);
                        spacesRules.configure(exchanges);
                        bookingsRules.configure(exchanges);
                                


                        // Bloquear cualquier operación no declarada.
                        exchanges.anyExchange().denyAll();
                })

                // Validar JWT y convertir los roles de Keycloak.
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(
                                        converter))))

                .build();
    }
}