package ar.edu.utn.frc.users.cliente;

import ar.edu.utn.frc.users.administrador.model.Administrador;
import ar.edu.utn.frc.users.administrador.repository.AdministradorRepository;
import ar.edu.utn.frc.users.cliente.repository.ClienteRepository;
import ar.edu.utn.frc.users.perfil.repository.PerfilUsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:registration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.gateway.internal-token=test-only-internal-token", "spring.config.import="
})
@AutoConfigureMockMvc
class RegistrarClienteIntegrationTest {

    private static final String ENDPOINT = "/api/users/clientes";
    private static final String BODY = """
            {"nombre":" Ana ","apellido":" Perez ","telefono":" 3511234567 ","preferenciaContacto":"TELEFONO"}
            """;
    @Autowired MockMvc mvc;
    @Autowired ClienteRepository clientes;
    @Autowired PerfilUsuarioRepository perfiles;
    @Autowired AdministradorRepository administradores;

    @Test
    void publicaContratoSinExponerCabecerasInternas() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/users/clientes'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/users/clientes'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.components.schemas.RegistrarClienteRequest.properties.contactoTelefonicoValido").doesNotExist());
    }

    @Test
    void creaPerfilConIdentidadInternaYRechazaDuplicado() throws Exception {
        String sub = UUID.randomUUID().toString();
        mvc.perform(post(ENDPOINT).header("X-Gateway-Token", "test-only-internal-token")
                .header("X-User-Id", sub).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.fechaAlta").isNotEmpty())
                .andExpect(jsonPath("$.keycloakId").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        assertThat(clientes.findByKeycloakId(sub)).isPresent();
        assertThat(perfiles.findByKeycloakId(sub)).isPresent();
        mvc.perform(post(ENDPOINT).header("X-Gateway-Token", "test-only-internal-token")
                .header("X-User-Id", sub).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void noConvierteAdministradorEnCliente() throws Exception {
        var administrador = new Administrador();
        String sub = UUID.randomUUID().toString();
        administrador.setKeycloakId(sub);
        administrador.setNombre("Admin");
        administrador.setApellido("Existente");
        administradores.saveAndFlush(administrador);
        mvc.perform(post(ENDPOINT).header("X-Gateway-Token", "test-only-internal-token")
                .header("X-User-Id", sub).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
        assertThat(clientes.findByKeycloakId(sub)).isEmpty();
    }

    @Test
    void rechazaAccesoDirectoAunqueSeInventenEncabezados() throws Exception {
        mvc.perform(post(ENDPOINT).header("X-User-Id", "victima")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(post(ENDPOINT).header("X-Gateway-Token", "incorrecto")
                .header("X-User-Id", "victima").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/%61pi/users/clientes").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void validaNombreTelefonoYRechazaRolesOIdentidadEnElJson() throws Exception {
        for (String body : new String[]{
                "{\"nombre\":\" \",\"apellido\":\"Perez\"}",
                "{\"nombre\":\"Ana\",\"apellido\":\"Perez\",\"preferenciaContacto\":\"TELEFONO\"}",
                "{\"nombre\":\"Ana\",\"apellido\":\"Perez\",\"rol\":\"ADMIN\"}",
                "{\"nombre\":\"Ana\",\"apellido\":\"Perez\",\"keycloakId\":\"otra-cuenta\"}",
                "{\"nombre\":\"Ana\",\"apellido\":\"Perez\",\"preferenciaContacto\":\"FAX\"}"}) {
            mvc.perform(post(ENDPOINT).header("X-Gateway-Token", "test-only-internal-token")
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void emailRequiereCorreoEnLaIdentidadYPermiteReintentar() throws Exception {
        String sub = UUID.randomUUID().toString();
        String body = "{\"nombre\":\"Ana\",\"apellido\":\"Perez\",\"preferenciaContacto\":\"EMAIL\"}";
        mvc.perform(post(ENDPOINT).header("X-Gateway-Token", "test-only-internal-token")
                .header("X-User-Id", sub).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(perfiles.findByKeycloakId(sub)).isEmpty();
        mvc.perform(post(ENDPOINT).header("X-Gateway-Token", "test-only-internal-token")
                .header("X-User-Id", sub).header("X-User-Email", "YW5hQGV4YW1wbGUuY29t")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }
}
