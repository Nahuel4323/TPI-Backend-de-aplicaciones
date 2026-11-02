package ar.edu.utn.frc.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            Object roles = realm == null ? null : realm.get("roles");
            if (!(roles instanceof List<?> values)) {
                return List.of();
            }
            return values.stream().filter(String.class::isInstance)
                    .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        });

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/webjars/swagger-ui/**",
                                "/v3/api-docs/swagger-config", "/docs/users").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/users/clientes", "/api/users/clientes/")
                        .access((authentication, context) -> authentication.<AuthorizationResult>map(auth -> {
                            boolean cliente = auth.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
                            boolean admin = auth.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                                            || a.getAuthority().equals("ROLE_ADMINISTRADOR"));
                            return new AuthorizationDecision(auth.isAuthenticated() && cliente && !admin);
                        }).defaultIfEmpty(new AuthorizationDecision(false)))
                        // Las otras US aun no tienen una politica de autorizacion implementada.
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        new ReactiveJwtAuthenticationConverterAdapter(converter))))
                .build();
    }
}
