package ar.edu.utn.frc.users.shared.keycloak;

import ar.edu.utn.frc.users.shared.exception.RegistroException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Cliente de administración: las credenciales de servicio nunca salen de users. */
@Component
public class KeycloakRegistrationClient {
    private final RestClient http;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> OBJECTS = new ParameterizedTypeReference<>() {};

    public KeycloakRegistrationClient(
            @Value("${app.keycloak.base-url}") String baseUrl,
            @Value("${app.keycloak.realm}") String realm,
            @Value("${app.keycloak.registration-client-id}") String clientId,
            @Value("${app.keycloak.registration-client-secret}") String clientSecret,
            @Value("${app.keycloak.timeout-seconds:10}") int timeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String token() {
        if (clientSecret.isBlank()) {
            throw unavailable();
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        try {
            var body = http.post().uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(OBJECT);
            if (body == null || !(body.get("access_token") instanceof String token) || token.isBlank()) {
                throw unavailable();
            }
            return token;
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    public String crearCuentaDeshabilitada(String token, DatosRegistro request) {
        return crearCuenta(token, request, "CLIENTE");
    }

    public String crearAdministradorDeshabilitado(String token, DatosRegistro request) {
        return crearCuenta(token, request, "ADMIN");
    }

    private String crearCuenta(String token, DatosRegistro request, String roleName) {
        String registrationId = UUID.randomUUID().toString();
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        try {
            // Obtener el rol antes del alta para fallar sin crear cuenta si falta configuración.
            var role = http.get().uri("/admin/realms/{realm}/roles/{role}", realm, roleName)
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(OBJECT);
            if (role == null || !(role.get("id") instanceof String)) throw unavailable();

            String userId;
            try {
                var response = http.post().uri("/admin/realms/{realm}/users", realm)
                        .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("username", email, "email", email, "enabled", false,
                                "emailVerified", false, "firstName", request.nombre().strip(),
                                "lastName", request.apellido().strip(),
                                "attributes", Map.of("registrationId", List.of(registrationId)),
                                "credentials", List.of(Map.of("type", "password", "temporary", false,
                                        "value", request.password()))))
                        .retrieve().toBodilessEntity();
                var location = response.getHeaders().getLocation();
                if (location == null) throw new IllegalArgumentException("Respuesta sin Location");
                String path = location.getPath();
                userId = UUID.fromString(path.substring(path.lastIndexOf('/') + 1)).toString();
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 409) {
                    throw new RegistroException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
                }
                if (ex.getStatusCode().value() == 400) {
                    throw new RegistroException(HttpStatus.BAD_REQUEST,
                            "Los datos o la contraseña no cumplen la política de registro de Keycloak");
                }
                if (!ex.getStatusCode().is5xxServerError()) throw ex;
                userId = recuperarCuentaCreada(token, email, registrationId);
                if (userId == null) throw unavailable();
            } catch (RestClientException | IllegalArgumentException ex) {
                // Una respuesta perdida no prueba que el alta haya fallado.
                // Recuperar solo la cuenta marcada por ESTA operación, nunca una preexistente.
                userId = recuperarCuentaCreada(token, email, registrationId);
                if (userId == null) throw unavailable();
            }

            try {
                if ("ADMIN".equals(roleName)) {
                    // La cuenta nueva hereda el rol compuesto predeterminado con CLIENTE.
                    // Retirar sus asignaciones directas antes de asignar ADMIN.
                    var defaults = http.get().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                            .headers(h -> h.setBearerAuth(token)).retrieve().body(OBJECTS);
                    if (defaults == null) throw unavailable();
                    if (!defaults.isEmpty()) {
                        http.method(org.springframework.http.HttpMethod.DELETE)
                                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                                .body(defaults).retrieve().toBodilessEntity();
                    }
                }
                http.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                        .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                        .body(List.of(role)).retrieve().toBodilessEntity();
                var effective = http.get().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm/composite", realm, userId)
                        .headers(h -> h.setBearerAuth(token)).retrieve().body(OBJECTS);
                var forbidden = "ADMIN".equals(roleName) ? List.of("CLIENTE", "ADMINISTRADOR") : List.of("ADMIN", "ADMINISTRADOR");
                if (effective == null || effective.stream().noneMatch(r -> roleName.equals(r.get("name")))
                        || effective.stream().anyMatch(r -> forbidden.stream().anyMatch(name -> name.equals(r.get("name"))))) {
                    throw unavailable();
                }
                return userId;
            } catch (RuntimeException ex) {
                eliminarCuenta(token, userId);
                throw unavailable();
            }
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    public String buscarAdministradorExistente(String token, String email) {
        try {
            var users = http.get().uri("/admin/realms/{realm}/users?username={email}&exact=true", realm,
                            email.strip().toLowerCase(Locale.ROOT))
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(OBJECTS);
            if (users == null || users.isEmpty()) return null;
            if (users.size() != 1 || !Boolean.TRUE.equals(users.getFirst().get("enabled"))) throw unavailable();
            String id = UUID.fromString((String) users.getFirst().get("id")).toString();
            var roles = http.get().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm/composite", realm, id)
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(OBJECTS);
            if (roles == null || roles.stream().noneMatch(r -> "ADMIN".equals(r.get("name")))
                    || roles.stream().anyMatch(r -> "CLIENTE".equals(r.get("name")))) {
                throw new RegistroException(HttpStatus.CONFLICT, "La cuenta existente no es un administrador válido");
            }
            return id;
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    private String recuperarCuentaCreada(String token, String email, String registrationId) {
        try {
            var users = http.get().uri("/admin/realms/{realm}/users?username={email}&exact=true", realm, email)
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(OBJECTS);
            if (users != null) {
                for (var user : users) {
                    if (user.get("attributes") instanceof Map<?, ?> attributes
                            && List.of(registrationId).equals(attributes.get("registrationId"))) {
                        return UUID.fromString((String) user.get("id")).toString();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Registrar solo la correlación; nunca correo, contraseña ni respuesta del proveedor.
        }
        org.slf4j.LoggerFactory.getLogger(getClass()).error(
                "Alta Keycloak de resultado incierto; reconciliar registrationId={}", registrationId);
        return null;
    }

    public void habilitarCuenta(String token, String id) {
        try {
            http.put().uri("/admin/realms/{realm}/users/{id}", realm, id)
                    .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", true)).retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    public void eliminarCuenta(String token, String id) {
        try {
            http.delete().uri("/admin/realms/{realm}/users/{id}", realm, id)
                    .headers(h -> h.setBearerAuth(token)).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return;
            compensacionFallida(id);
        } catch (RestClientException ex) {
            compensacionFallida(id);
        }
    }

    private void compensacionFallida(String id) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error(
                "No se pudo compensar el registro; reconciliar usuario Keycloak id={}", id);
        throw new RegistroException(HttpStatus.SERVICE_UNAVAILABLE,
                "El registro no se completó y requiere reconciliación; contactar al administrador");
    }

    private RegistroException unavailable() {
        return new RegistroException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo completar el registro con el servicio de identidad; reintentar más tarde");
    }
}
