package ar.edu.utn.frc.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba HTTP del gateway. El decoder de este test no verifica criptografia; se prueba aparte con Keycloak real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import="})
@Import(GatewaySecurityIntegrationTest.Tokens.class)
class GatewaySecurityIntegrationTest {

    private static final AtomicReference<com.sun.net.httpserver.Headers> HEADERS = new AtomicReference<>();
    private static final HttpServer UPSTREAM;
    static {
        try {
            UPSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            UPSTREAM.createContext("/api/users/clientes", exchange -> {
                HEADERS.set(exchange.getRequestHeaders());
                byte[] body = "{\"id\":\"perfil-prueba\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            UPSTREAM.createContext("/api/users/administradores", exchange -> {
                HEADERS.set(exchange.getRequestHeaders());
                byte[] body = "{\"id\":\"admin-prueba\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            UPSTREAM.createContext("/api/users/me", exchange -> {
                HEADERS.set(exchange.getRequestHeaders());
                byte[] body = "{\"nombre\":\"Perfil propio\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            UPSTREAM.createContext("/v3/api-docs", exchange -> {
                byte[] body = "{\"openapi\":\"3.1.0\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            UPSTREAM.createContext("/api/spaces/deportes", exchange -> {
                HEADERS.set(exchange.getRequestHeaders());
                byte[] body = "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            UPSTREAM.start();
        } catch (java.io.IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.server.webflux.routes[2].id", () -> "spaces-test");
        registry.add("spring.cloud.gateway.server.webflux.routes[2].predicates[0]", () -> "Path=/api/spaces/**");
        registry.add("spring.cloud.gateway.server.webflux.routes[2].uri",
                () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
        registry.add("spring.cloud.gateway.server.webflux.routes[1].id", () -> "users-openapi");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].predicates[0]", () -> "Path=/docs/users");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].predicates[1]", () -> "Method=GET");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].filters[0]", () -> "SetPath=/v3/api-docs");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].uri",
                () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "users-test");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]", () -> "Path=/api/users/**");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri",
                () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stop() { UPSTREAM.stop(0); }

    private HttpResponse<String> registrar(String token) throws Exception {
        return registrarEn("/api/users/clientes", token);
    }

    private HttpResponse<String> registrarEn(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-User-Id", "identidad-inventada")
                .header("X-User-Email", "correo-inventado")
                .header("X-User-Roles", "ADMIN")
                .header("X-Gateway-Token", "clave-inventada")
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void patchPropioExigeTokenYReemplazaIdentidadDelConsumidor() throws Exception {
        for (String token : List.of("", "invalido", "cliente", "admin")) {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/users/me"))
                    .header("Content-Type", "application/json").header("X-User-Id", "victima")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"nombre\":\"Perfil propio\"}"));
            if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(token.isEmpty() || token.equals("invalido") ? 401 : 200);
            if (response.statusCode() == 200) {
                assertThat(HEADERS.get().getFirst("X-User-Id")).isEqualTo("identidad-real");
                assertThat(HEADERS.get().getFirst("Authorization")).isNull();
            }
        }
    }

    @Test
    void altaAdministradorExigeAdminEnGateway() throws Exception {
        assertThat(registrarEn("/api/users/administradores", null).statusCode()).isEqualTo(401);
        assertThat(registrarEn("/api/users/administradores", "invalido").statusCode()).isEqualTo(401);
        assertThat(registrarEn("/api/users/administradores", "cliente").statusCode()).isEqualTo(403);
        assertThat(registrarEn("/api/users/administradores", "sinroles").statusCode()).isEqualTo(403);
        assertThat(registrarEn("/api/users/administradores", "admin").statusCode()).isEqualTo(201);
        assertThat(HEADERS.get().getFirst("X-User-Id")).isEqualTo("identidad-real");
        assertThat(HEADERS.get().getFirst("X-User-Roles")).isNull();
        assertThat(registrarEn("/api/users/administradores/", "admin").statusCode()).isEqualTo(201);
    }

    @Test
    void permiteCatalogoPublicoPeroProtegePerfilYOperacionesInternas() throws Exception {
        var catalogo = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/spaces/deportes"))
                .header("X-User-Id", "inventado")
                .header("X-Gateway-Token", "inventado").GET().build();
        assertThat(client.send(catalogo, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        assertThat(HEADERS.get().getFirst("X-User-Id")).isNull();
        assertThat(HEADERS.get().getFirst("X-Gateway-Token")).isNull();

        var perfil = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/users/me"))
                .GET().build();
        assertThat(client.send(perfil, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        var interna = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/spaces/ocupaciones/retenciones"))
                .header("Authorization", "Bearer admin")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertThat(client.send(interna, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void publicaSwaggerYContratoSinToken() throws Exception {
        for (String path : List.of("/webjars/swagger-ui/index.html", "/v3/api-docs/swagger-config", "/docs/users")) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.body()).isNotBlank();
        }
    }

    @Test
    void registroPublicoLimpiaEncabezadosSinToken() throws Exception {
        assertThat(registrar(null).statusCode()).isEqualTo(201);
        var headers = HEADERS.get();
        assertThat(headers.getFirst("X-User-Id")).isNull();
        assertThat(headers.getFirst("X-User-Email")).isNull();
        assertThat(headers.getFirst("X-User-Roles")).isNull();
        assertThat(headers.getFirst("X-Gateway-Token")).isNull();
        assertThat(headers.getFirst("Authorization")).isNull();
    }

    @Test
    void rechazaTokenInvalidoInclusoEnRegistroPublico() throws Exception {
        assertThat(registrar("invalido").statusCode()).isEqualTo(401);
    }

    @Test
    void registroPublicoNoExigeRol() throws Exception {
        for (String token : List.of("admin", "doble", "sinroles")) {
            assertThat(registrar(token).statusCode()).isEqualTo(201);
        }
    }

    @Test
    void reemplazaIdentidadYClaveYNoReenviaTokenDeUsuario() throws Exception {
        assertThat(registrar("cliente").statusCode()).isEqualTo(201);
        var headers = HEADERS.get();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("identidad-real");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("YW5hQGV4YW1wbGUuY29t");
        assertThat(headers.getFirst("X-Gateway-Token")).isNull();
        assertThat(headers.getFirst("X-User-Roles")).isNull();
        assertThat(headers.getFirst("Authorization")).isNull();
    }

    @TestConfiguration
    static class Tokens {
        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> {
                if (token.equals("invalido")) return Mono.error(new BadJwtException("Token invalido"));
                List<String> roles = switch (token) {
                    case "cliente" -> List.of("CLIENTE");
                    case "admin" -> List.of("ADMIN");
                    case "doble" -> List.of("CLIENTE", "ADMIN");
                    default -> List.of();
                };
                return Mono.just(Jwt.withTokenValue(token).header("alg", "RS256")
                        .subject("identidad-real").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                        .claim("realm_access", Map.of("roles", roles)).claim("email", "ana@example.com").build());
            };
        }
    }
}
