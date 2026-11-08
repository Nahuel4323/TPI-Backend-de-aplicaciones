package ar.edu.utn.frc.users.cliente;

import ar.edu.utn.frc.users.cliente.repository.ClienteRepository;
import ar.edu.utn.frc.users.perfil.repository.PerfilUsuarioRepository;
import ar.edu.utn.frc.users.administrador.repository.AdministradorRepository;
import ar.edu.utn.frc.users.administrador.service.AdministradorService;
import ar.edu.utn.frc.users.administrador.dto.RegistrarAdministradorRequest;
import ar.edu.utn.frc.users.shared.exception.RegistroException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP real hacia un stand-in de Keycloak; validación, JPA y transacciones reales sobre H2. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:registration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.config.import=",
    "app.keycloak.registration-client-secret=test-service-secret", "app.keycloak.timeout-seconds=1"
})
@AutoConfigureMockMvc
class RegistrarClienteIntegrationTest {
    static final String ENDPOINT = "/api/users/clientes";
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Map<String, Map<String, Object>> ACCOUNTS = new ConcurrentHashMap<>();
    static final List<String> CALLS = Collections.synchronizedList(new ArrayList<>());
    static volatile int failCreate, failEnable, failDelete, failRole;
    static volatile boolean missingLocation, inheritedAdmin;
    static final HttpServer KEYCLOAK;
    static {
        try {
            KEYCLOAK = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            KEYCLOAK.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
                CALLS.add(method + " " + path);
                int status = 200;
                Object response = Map.of();
                if (path.endsWith("/token")) {
                    String form = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    if (!form.contains("grant_type=client_credentials") || !form.contains("client_secret=test-service-secret")) status = 401;
                    else response = Map.of("access_token", "service-token");
                } else if (!"Bearer service-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) status = 401;
                else if (path.endsWith("/roles/CLIENTE")) response = Map.of("id", "role-cliente", "name", "CLIENTE");
                else if (path.endsWith("/roles/ADMIN")) response = Map.of("id", "role-admin", "name", "ADMIN");
                else if (path.endsWith("/users") && method.equals("POST")) {
                    if (failCreate != 0) status = failCreate;
                    else {
                        @SuppressWarnings("unchecked")
                        var body = (Map<String, Object>) JSON.readValue(exchange.getRequestBody().readAllBytes(), Map.class);
                        if (ACCOUNTS.values().stream().anyMatch(u -> u.get("username").equals(body.get("username")))) status = 409;
                        else {
                            String id = UUID.randomUUID().toString();
                            body.put("id", id); body.remove("credentials");
                            ACCOUNTS.put(id, body);
                            if (!missingLocation) exchange.getResponseHeaders().set("Location", "/admin/realms/complejos-deportivos/users/" + id);
                            status = 201;
                        }
                    }
                } else if (path.endsWith("/users") && method.equals("GET")) {
                    String query = exchange.getRequestURI().getRawQuery();
                    String email = java.net.URLDecoder.decode(query.substring("username=".length()).split("&")[0], java.nio.charset.StandardCharsets.UTF_8);
                    response = ACCOUNTS.values().stream().filter(a -> email.equals(a.get("username"))).toList();
                } else if (path.endsWith("/role-mappings/realm/composite")) {
                    String id = path.split("/users/")[1].split("/")[0];
                    response = inheritedAdmin ? List.of(Map.of("name", "CLIENTE"), Map.of("name", "ADMIN"))
                            : List.of(Map.of("name", ACCOUNTS.get(id).getOrDefault("assignedRole", "CLIENTE")));
                } else if (path.endsWith("/role-mappings/realm") && method.equals("GET")) {
                    response = List.of(Map.of("id", "role-default", "name", "default-roles-complejos-deportivos"));
                } else if (path.endsWith("/role-mappings/realm") && method.equals("DELETE")) {
                    String id = path.split("/users/")[1].split("/")[0];
                    ACCOUNTS.get(id).put("defaultsRemoved", true);
                    status = 204;
                }
                else if (path.endsWith("/role-mappings/realm") && method.equals("POST")) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    String id = path.split("/users/")[1].split("/")[0];
                    boolean admin = body.contains("\"ADMIN\"");
                    status = failRole != 0 ? failRole : (admin || body.contains("CLIENTE") ? 204 : 400);
                    if (status == 204) ACCOUNTS.get(id).put("assignedRole", admin ? "ADMIN" : "CLIENTE");
                } else if (path.contains("/users/") && method.equals("PUT")) {
                    if (failEnable != 0) status = failEnable;
                    else { ACCOUNTS.get(path.substring(path.lastIndexOf('/') + 1)).put("enabled", true); status = 204; }
                } else if (path.contains("/users/") && method.equals("DELETE")) {
                    if (failDelete != 0) status = failDelete;
                    else { ACCOUNTS.remove(path.substring(path.lastIndexOf('/') + 1)); status = 204; }
                } else status = 501;
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] bytes = JSON.writeValueAsBytes(response);
                exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
                if (status != 204) exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            KEYCLOAK.start();
        } catch (java.io.IOException ex) { throw new ExceptionInInitializerError(ex); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("app.keycloak.base-url", () -> "http://127.0.0.1:" + KEYCLOAK.getAddress().getPort());
    }
    @Autowired MockMvc mvc;
    @MockitoSpyBean ClienteRepository clientes;
    @Autowired PerfilUsuarioRepository perfiles;
    @MockitoSpyBean AdministradorRepository administradores;
    @Autowired AdministradorService administradorService;

    @BeforeEach void limpiar() {
        perfiles.deleteAll(); ACCOUNTS.clear(); CALLS.clear(); failCreate = failEnable = failDelete = failRole = 0;
        missingLocation = inheritedAdmin = false;
    }
    @AfterAll static void stop() { KEYCLOAK.stop(0); }

    String body(String email) {
        return """
            {"email":"%s","password":"RegistroPrueba123!","nombre":" Ana ",
             "apellido":" Perez ","telefono":"3511234567","preferenciaContacto":"EMAIL"}
            """.formatted(email);
    }

    @Test void contratoPublico() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/users/clientes'].post.responses['201']").exists())
            .andExpect(jsonPath("$.paths['/api/users/clientes'].post.security").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.RegistrarClienteRequest.properties.password.writeOnly").value(true));
    }

    @Test void registroAnonimoIgnoraIdentidadInventada() throws Exception {
        mvc.perform(post(ENDPOINT).header("X-User-Id", "victima").header("X-Gateway-Token", "inventado")
                .contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value("Ana"))
            .andExpect(jsonPath("$.fechaAlta").isNotEmpty()).andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.keycloakId").doesNotExist());
        String id = ACCOUNTS.keySet().iterator().next();
        assertThat(clientes.findByKeycloakId(id)).isPresent();
        assertThat(clientes.findByKeycloakId("victima")).isEmpty();
        assertThat(ACCOUNTS.get(id).get("enabled")).isEqualTo(true);
        assertThat(CALLS).contains("POST /admin/realms/complejos-deportivos/users/" + id + "/role-mappings/realm");
    }

    @Test void duplicadoNoEliminaCuentaExistente() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com"))).andExpect(status().isCreated());
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ANA@example.com")))
            .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(clientes.count()).isEqualTo(1); assertThat(ACCOUNTS).hasSize(1);
        assertThat(CALLS).noneMatch(c -> c.startsWith("DELETE"));
    }

    @Test void validacionesAntesDeLlamarKeycloak() throws Exception {
        for (String b : List.of(body("invalido"), body("ana@example.com").replace("RegistroPrueba123!", "corta"),
                body("ana@example.com").replace(" Ana ", " "),
                body("ana@example.com").replace("\"EMAIL\"", "\"TELEFONO\"").replace("3511234567", ""),
                body("ana@example.com").replace("\"email\":", "\"rol\":\"ADMIN\",\"email\":"),
                body("ana@example.com").replace("\"email\":", "\"keycloakId\":\"victima\",\"email\":"))) {
            mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isBadRequest());
        }
        assertThat(CALLS).isEmpty(); assertThat(perfiles.count()).isZero();
    }

    @Test void keycloakCaido() throws Exception {
        failCreate = 503;
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com")))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
        assertThat(perfiles.count()).isZero();
    }

    @Test void politicaPasswordInvalida() throws Exception {
        failCreate = 400;
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com")))
            .andExpect(status().isBadRequest());
        assertThat(perfiles.count()).isZero();
    }

    @Test void falloBaseCompensaYPermiteReintento() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("fallo simulado")).when(clientes).saveAndFlush(any());
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com"))).andExpect(status().isConflict());
        assertThat(ACCOUNTS).isEmpty(); assertThat(perfiles.count()).isZero();
        reset(clientes);
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com"))).andExpect(status().isCreated());
    }

    @Test void falloAlHabilitarRevierteAmbos() throws Exception {
        failEnable = 503;
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com"))).andExpect(status().isServiceUnavailable());
        assertThat(perfiles.count()).isZero(); assertThat(ACCOUNTS).isEmpty();
    }

    @Test void compensacionFallidaInformaReconciliacion() throws Exception {
        failEnable = failDelete = 503;
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com")))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reconciliación")));
        assertThat(perfiles.count()).isZero(); assertThat(ACCOUNTS).hasSize(1);
        assertThat(ACCOUNTS.values().iterator().next().get("enabled")).isEqualTo(false);
    }

    @Test void barraFinal() throws Exception {
        mvc.perform(post(ENDPOINT + "/").contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com"))).andExpect(status().isCreated());
    }

    @Test void recuperaAltaSinLocationSoloSiTieneLaCorrelacionPropia() throws Exception {
        missingLocation = true;
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com")))
            .andExpect(status().isCreated());
        assertThat(ACCOUNTS).hasSize(1); assertThat(perfiles.count()).isEqualTo(1);
        assertThat(CALLS).contains("GET /admin/realms/complejos-deportivos/users");
    }

    @Test void falloAsignacionDeRolEliminaCuentaAntesDeCrearPerfil() throws Exception {
        failRole = 403;
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com")))
            .andExpect(status().isServiceUnavailable());
        assertThat(ACCOUNTS).isEmpty(); assertThat(perfiles.count()).isZero();
    }

    @Test void impideHerenciaAdministrativaPorConfiguracionIncorrecta() throws Exception {
        inheritedAdmin = true;
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("ana@example.com")))
            .andExpect(status().isServiceUnavailable());
        assertThat(ACCOUNTS).isEmpty(); assertThat(perfiles.count()).isZero();
    }

    String adminBody(String email) {
        return """
            {"email":"%s","password":"RegistroPrueba123!","nombre":" Admin ",
             "apellido":" Perez ","telefono":"3511234567","telefonoLaboral":" 3517654321 "}
            """.formatted(email);
    }

    @Test void registraAdministradorSinRolClienteNiAsignacionDeComplejo() throws Exception {
        mvc.perform(post("/api/users/administradores").contentType(MediaType.APPLICATION_JSON).content(adminBody("admin@example.com")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value("Admin"))
            .andExpect(jsonPath("$.telefonoLaboral").value("3517654321"))
            .andExpect(jsonPath("$.fechaAlta").isNotEmpty()).andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.keycloakId").doesNotExist()).andExpect(jsonPath("$.complejoId").doesNotExist());
        String id = ACCOUNTS.keySet().iterator().next();
        assertThat(administradores.findByKeycloakId(id)).isPresent();
        assertThat(clientes.findByKeycloakId(id)).isEmpty();
        assertThat(ACCOUNTS.get(id)).containsEntry("assignedRole", "ADMIN").containsEntry("defaultsRemoved", true).containsEntry("enabled", true);
        String rolesPath = "/admin/realms/complejos-deportivos/users/" + id + "/role-mappings/realm";
        assertThat(CALLS.indexOf("DELETE " + rolesPath)).isLessThan(CALLS.indexOf("POST " + rolesPath));
    }

    @Test void administradorDuplicadoYClienteExistenteNoSePromueven() throws Exception {
        mvc.perform(post("/api/users/administradores").contentType(MediaType.APPLICATION_JSON).content(adminBody("admin@example.com"))).andExpect(status().isCreated());
        mvc.perform(post("/api/users/administradores").contentType(MediaType.APPLICATION_JSON).content(adminBody("ADMIN@example.com"))).andExpect(status().isConflict());
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("cliente@example.com"))).andExpect(status().isCreated());
        mvc.perform(post("/api/users/administradores").contentType(MediaType.APPLICATION_JSON).content(adminBody("cliente@example.com"))).andExpect(status().isConflict());
        assertThat(administradores.count()).isEqualTo(1); assertThat(clientes.count()).isEqualTo(1);
        assertThat(CALLS).noneMatch(c -> c.startsWith("DELETE /admin/realms/complejos-deportivos/users/") && !c.endsWith("/role-mappings/realm"));
    }

    @Test void administradorValidaDatosYNoAdmiteRolIdentidadNiComplejo() throws Exception {
        for (String b : List.of(adminBody("invalido"), adminBody("admin@example.com").replace("RegistroPrueba123!", "corta"),
                adminBody("admin@example.com").replace(" Admin ", " "),
                adminBody("admin@example.com").replace("\"email\":", "\"rol\":\"ADMIN\",\"email\":"),
                adminBody("admin@example.com").replace("\"email\":", "\"keycloakId\":\"otra-cuenta\",\"email\":"),
                adminBody("admin@example.com").replace("\"email\":", "\"complejoId\":\"" + UUID.randomUUID() + "\",\"email\":"))) {
            mvc.perform(post("/api/users/administradores").contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isBadRequest());
        }
        assertThat(CALLS).isEmpty(); assertThat(perfiles.count()).isZero();
    }

    @Test void administradorFalloBaseCompensaYPermiteReintento() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("fallo simulado")).when(administradores).saveAndFlush(any());
        mvc.perform(post("/api/users/administradores").contentType(MediaType.APPLICATION_JSON).content(adminBody("admin@example.com"))).andExpect(status().isConflict());
        assertThat(ACCOUNTS).isEmpty(); assertThat(perfiles.count()).isZero();
        reset(administradores);
        mvc.perform(post("/api/users/administradores").contentType(MediaType.APPLICATION_JSON).content(adminBody("admin@example.com"))).andExpect(status().isCreated());
    }

    @Test void administradorNoHabilitaCuentaConRolesIncompatibles() throws Exception {
        inheritedAdmin = true;
        mvc.perform(post("/api/users/administradores").contentType(MediaType.APPLICATION_JSON).content(adminBody("admin@example.com"))).andExpect(status().isServiceUnavailable());
        assertThat(ACCOUNTS).isEmpty(); assertThat(perfiles.count()).isZero();
    }

    @Test void administradorFalloHabilitacionRevierteAmbos() throws Exception {
        failEnable = 503;
        mvc.perform(post("/api/users/administradores").contentType(MediaType.APPLICATION_JSON).content(adminBody("admin@example.com"))).andExpect(status().isServiceUnavailable());
        assertThat(ACCOUNTS).isEmpty(); assertThat(perfiles.count()).isZero();
    }

    @Test void administradorContratoProtegido() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/users/administradores'].post.security[0].bearerAuth").exists())
            .andExpect(jsonPath("$.components.schemas.RegistrarAdministradorRequest.properties.password.writeOnly").value(true));
    }

    @Test void bootstrapIdempotenteYNoPermiteInicializarOtroAdministrador() {
        var request = new RegistrarAdministradorRequest("seed@example.com", "RegistroPrueba123!", "Inicial", "Admin", null, null);
        var first = administradorService.registrarInicial(request);
        var second = administradorService.registrarInicial(request);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(administradores.count()).isEqualTo(1); assertThat(ACCOUNTS).hasSize(1);
        var other = new RegistrarAdministradorRequest("otro@example.com", "RegistroPrueba123!", "Otro", "Admin", null, null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> administradorService.registrarInicial(other))
            .isInstanceOf(RegistroException.class);
        assertThat(ACCOUNTS).hasSize(1);
    }
}
