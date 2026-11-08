package ar.edu.utn.frc.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;


//Esta clase sirve para:
// - Limpiar los encabezados de identidad enviados por el cliente.
// - Si hay un usuario autenticado, agregar su identidad validada.
// - Continuar el envío al microservicio correspondiente.
@Component
public class TrustedIdentityFilter implements GlobalFilter, Ordered {


    
    // Exchange: contiene la peticion, respuesta y el usuario autenticado
    // chain: permite seguir con la ejecucion
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        
        // Obtenemos la ruta 
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);


        if (route != null && "users-openapi".equals(route.getId())) {
            var request = exchange.getRequest().mutate().headers(headers -> {
                headers.remove("Authorization");
                new ArrayList<>(headers.headerNames()).stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("x-user-")
                                || name.equalsIgnoreCase("X-Gateway-Token"))
                        .forEach(headers::remove);
            }).build();
            return chain.filter(exchange.mutate().request(request).build());
        }
        // Spring Security ya autorizó la solicitud antes de ejecutar los GlobalFilter.
        // Sin principal, solo llegan las rutas permitAll: reenviar sin identidad.
        // Resolver primero el intercambio evita usar switchIfEmpty sobre Mono<Void>,
        // lo que podría ejecutar la cadena dos veces.
        return exchange.getPrincipal().map(Optional::of)
                .defaultIfEmpty(Optional.empty()).flatMap(optionalPrincipal -> {
            if (optionalPrincipal.isEmpty()) {
                var request = exchange.getRequest().mutate().headers(headers -> {
                    headers.remove("Authorization");
                    new java.util.ArrayList<>(headers.headerNames()).stream()
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("x-user-")
                                    || name.equalsIgnoreCase("X-Gateway-Token"))
                            .forEach(headers::remove);
                }).build();
                return chain.filter(exchange.mutate().request(request).build());
            }
            var principal = optionalPrincipal.get();
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
