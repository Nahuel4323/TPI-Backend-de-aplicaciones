package ar.edu.utn.frc.users.perfil;

import ar.edu.utn.frc.users.administrador.model.Administrador;
import ar.edu.utn.frc.users.administrador.repository.AdministradorRepository;
import ar.edu.utn.frc.users.cliente.enums.CanalContacto;
import ar.edu.utn.frc.users.cliente.model.Cliente;
import ar.edu.utn.frc.users.cliente.repository.ClienteRepository;
import ar.edu.utn.frc.users.perfil.repository.PerfilUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:perfil;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.config.import=",
        "app.bootstrap-admin.enabled=false"
})
@AutoConfigureMockMvc
class PerfilPropioIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ClienteRepository clientes;
    @Autowired AdministradorRepository administradores;
    @Autowired PerfilUsuarioRepository perfiles;
    Cliente cliente, otroCliente;
    Administrador admin;

    @BeforeEach
    void preparar() {
        perfiles.deleteAll();
        cliente = crearCliente("Inicial");
        otroCliente = crearCliente("Otro");
        admin = new Administrador();
        admin.setKeycloakId(UUID.randomUUID().toString());
        admin.setNombre("Admin"); admin.setApellido("Original");
        admin.setTelefono("3511234567"); admin.setTelefonoLaboral("3517654321");
        admin = administradores.saveAndFlush(admin);
    }

    private Cliente crearCliente(String nombre) {
        var c = new Cliente();
        c.setKeycloakId(UUID.randomUUID().toString());
        c.setNombre(nombre); c.setApellido("Original");
        c.setTelefono("3511234567"); c.setPreferenciaContacto(CanalContacto.EMAIL);
        return clientes.saveAndFlush(c);
    }

    @Test void getDevuelveClienteYAdministrador() throws Exception {
        mvc.perform(get("/api/users/me").header("X-User-Id", cliente.getKeycloakId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(cliente.getId().toString()))
                .andExpect(jsonPath("$.tipoPerfil").value("CLIENTE"))
                .andExpect(jsonPath("$.preferenciaContacto").value("EMAIL"))
                .andExpect(jsonPath("$.keycloakId").doesNotExist());
        mvc.perform(get("/api/users/me").header("X-User-Id", admin.getKeycloakId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipoPerfil").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.preferenciaContacto").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test void actualizaNombreSinModificarOtrosCamposNiOtroUsuario() throws Exception {
        mvc.perform(patch("/api/users/me").header("X-User-Id", cliente.getKeycloakId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\" Nombre nuevo \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Nombre nuevo"))
                .andExpect(jsonPath("$.apellido").value("Original"))
                .andExpect(jsonPath("$.telefono").value("3511234567"));
        var guardado = clientes.findById(cliente.getId()).orElseThrow();
        assertThat(guardado.getNombre()).isEqualTo("Nombre nuevo");
        assertThat(guardado.getKeycloakId()).isEqualTo(cliente.getKeycloakId());
        assertThat(guardado.isActivo()).isTrue();
        assertThat(clientes.findById(otroCliente.getId()).orElseThrow().getNombre()).isEqualTo("Otro");
    }

    @Test void nullConservaValorYTelefonoConEspaciosSeNormaliza() throws Exception {
        mvc.perform(patch("/api/users/me").header("X-User-Id", cliente.getKeycloakId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":null,\"apellido\":\" Nuevo \",\"telefono\":\" 3515555555 \",\"preferenciaContacto\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Inicial"))
                .andExpect(jsonPath("$.apellido").value("Nuevo"))
                .andExpect(jsonPath("$.telefono").value("3515555555"))
                .andExpect(jsonPath("$.preferenciaContacto").value("EMAIL"));
    }

    @Test void canalTelefonicoUsaTelefonoExistente() throws Exception {
        mvc.perform(patch("/api/users/me").header("X-User-Id", cliente.getKeycloakId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"preferenciaContacto\":\"TELEFONO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferenciaContacto").value("TELEFONO"));
        assertThat(clientes.findById(cliente.getId()).orElseThrow().getPreferenciaContacto()).isEqualTo(CanalContacto.TELEFONO);
    }

    @Test void noPermiteBorrarTelefonoSiCanalActualEsTelefonicoYNoGuardaCambiosParciales() throws Exception {
        cliente.setPreferenciaContacto(CanalContacto.TELEFONO); clientes.saveAndFlush(cliente);
        mvc.perform(patch("/api/users/me").header("X-User-Id", cliente.getKeycloakId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"No guardar\",\"telefono\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.detail").isNotEmpty());
        var guardado = clientes.findById(cliente.getId()).orElseThrow();
        assertThat(guardado.getNombre()).isEqualTo("Inicial");
        assertThat(guardado.getTelefono()).isEqualTo("3511234567");
    }

    @Test void cambioAEmailPermiteBorrarTelefonoEnLaMismaPeticion() throws Exception {
        cliente.setPreferenciaContacto(CanalContacto.TELEFONO); clientes.saveAndFlush(cliente);
        mvc.perform(patch("/api/users/me").header("X-User-Id", cliente.getKeycloakId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"\",\"preferenciaContacto\":\"EMAIL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telefono").value(org.hamcrest.Matchers.nullValue()));
        assertThat(clientes.findById(cliente.getId()).orElseThrow().getTelefono()).isNull();
    }

    @Test void noPermiteCanalTelefonicoSinTelefono() throws Exception {
        cliente.setTelefono(null); clientes.saveAndFlush(cliente);
        mvc.perform(patch("/api/users/me").header("X-User-Id", cliente.getKeycloakId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"preferenciaContacto\":\"TELEFONO\"}"))
                .andExpect(status().isBadRequest());
        assertThat(clientes.findById(cliente.getId()).orElseThrow().getPreferenciaContacto()).isEqualTo(CanalContacto.EMAIL);
    }

    @Test void administradorActualizaDatosPersonalesPeroNoPreferencia() throws Exception {
        mvc.perform(patch("/api/users/me").header("X-User-Id", admin.getKeycloakId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nuevo admin\",\"telefono\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Nuevo admin"))
                .andExpect(jsonPath("$.preferenciaContacto").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(patch("/api/users/me").header("X-User-Id", admin.getKeycloakId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"No guardar\",\"preferenciaContacto\":\"EMAIL\"}"))
                .andExpect(status().isBadRequest());
        var guardado = administradores.findById(admin.getId()).orElseThrow();
        assertThat(guardado.getNombre()).isEqualTo("Nuevo admin");
        assertThat(guardado.getTelefonoLaboral()).isEqualTo("3517654321");
    }

    @Test void rechazaCamposInvalidosOProhibidosSinModificarElPerfil() throws Exception {
        for (String body : List.of("{}", "{\"nombre\":null}", "{\"nombre\":\" \"}", "{\"apellido\":\"\"}",
                "{\"nombre\":\"" + "a".repeat(101) + "\"}", "{\"telefono\":\"" + "1".repeat(31) + "\"}",
                "{\"preferenciaContacto\":\"FAX\"}", "{\"activo\":false}", "{\"rol\":\"ADMIN\"}",
                "{\"id\":\"" + otroCliente.getId() + "\"}", "{\"keycloakId\":\"otro\"}",
                "{\"email\":\"otro@example.com\"}", "{\"password\":\"OtraClave123!\"}",
                "{\"telefonoLaboral\":\"123\"}", "null")) {
            mvc.perform(patch("/api/users/me").header("X-User-Id", cliente.getKeycloakId())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertThat(clientes.findById(cliente.getId()).orElseThrow().getNombre()).isEqualTo("Inicial");
    }

    @Test void getYPatchRechazanIdentidadAusentePerfilInexistenteEInactivo() throws Exception {
        cliente.setActivo(false); clientes.saveAndFlush(cliente);
        for (String method : List.of("GET", "PATCH")) {
            var missing = method.equals("GET") ? get("/api/users/me") : patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nuevo\"}");
            mvc.perform(missing).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
            var unknown = method.equals("GET") ? get("/api/users/me") : patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nuevo\"}");
            mvc.perform(unknown.header("X-User-Id", UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
            var inactive = method.equals("GET") ? get("/api/users/me") : patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nuevo\"}");
            mvc.perform(inactive.header("X-User-Id", cliente.getKeycloakId()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        }
        assertThat(clientes.findById(cliente.getId()).orElseThrow().getNombre()).isEqualTo("Inicial");
    }
}
