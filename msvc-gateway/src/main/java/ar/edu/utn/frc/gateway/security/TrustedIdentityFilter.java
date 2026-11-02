package ar.edu.utn.frc.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

@Component
public class TrustedIdentityFilter implements GlobalFilter, Ordered {

    private final String internalToken;

    public TrustedIdentityFilter(@Value("${app.gateway.internal-token}") String token) {
        if (token.isBlank()) {
            throw new IllegalArgumentException("GATEWAY_INTERNAL_TOKEN no puede estar vacio");
        }
        this.internalToken = token;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        org.springframework.cloud.gateway.route.Route route = exchange.getAttribute(
                org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null && "users-openapi".equals(route.getId())) {
            var request = exchange.getRequest().mutate().headers(headers -> {
                headers.remove("Authorization");
                new java.util.ArrayList<>(headers.headerNames()).stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("x-user-")
                                || name.equalsIgnoreCase("X-Gateway-Token"))
                        .forEach(headers::remove);
            }).build();
            return chain.filter(exchange.mutate().request(request).build());
        }
        return exchange.getPrincipal().flatMap(principal -> {
            if (!(principal instanceof JwtAuthenticationToken authentication)
                    || authentication.getToken().getSubject() == null
                    || authentication.getToken().getSubject().isBlank()) {
                org.slf4j.LoggerFactory.getLogger(TrustedIdentityFilter.class)
                        .warn("Identidad autenticada sin sub utilizable; tipo={}", principal.getClass().getName());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            var jwt = authentication.getToken();
            var request = exchange.getRequest().mutate().headers(headers -> {
                // Nunca confiar en identidad, rol o clave interna provistos por el consumidor.
                new java.util.ArrayList<>(headers.headerNames()).stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("x-user-")
                                || name.equalsIgnoreCase("X-Gateway-Token"))
                        .forEach(headers::remove);
                headers.remove("Authorization");
                headers.set("X-Gateway-Token", internalToken);
                headers.set("X-User-Id", jwt.getSubject());
                String email = jwt.getClaimAsString("email");
                if (email != null && !email.isBlank()) {
                    headers.set("X-User-Email", Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(email.getBytes(StandardCharsets.UTF_8)));
                }
            }).build();
            return chain.filter(exchange.mutate().request(request).build());
        });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
