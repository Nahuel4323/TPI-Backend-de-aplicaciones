package ar.edu.utn.frc.users.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Control del canal interno; la autenticacion y los roles se validan en el gateway. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayOnlyFilter extends OncePerRequestFilter {

    private final byte[] expectedToken;

    public GatewayOnlyFilter(@Value("${app.gateway.internal-token}") String token) {
        if (token.isBlank()) {
            throw new IllegalArgumentException("GATEWAY_INTERNAL_TOKEN no puede estar vacio");
        }
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Permitir solo health sin clave. No basar la proteccion en un prefijo
        // que una URL codificada pueda representar de otra manera.
        if (!("GET".equals(request.getMethod()) && path.equals("/v3/api-docs"))
                && !path.equals("/actuator/health") && !path.startsWith("/actuator/health/")) {
            String supplied = request.getHeader("X-Gateway-Token");
            String subject = request.getHeader("X-User-Id");
            if (supplied == null || !MessageDigest.isEqual(expectedToken,
                    supplied.getBytes(StandardCharsets.UTF_8))
                    || subject == null || subject.isBlank() || subject.length() > 255) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"title\":\"Acceso denegado\",\"status\":403,"
                        + "\"detail\":\"Esta operacion debe pasar por el gateway\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
