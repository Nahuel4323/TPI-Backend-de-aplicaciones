package ar.edu.utn.frc.users.administrador.config;

import ar.edu.utn.frc.users.administrador.dto.RegistrarAdministradorRequest;
import ar.edu.utn.frc.users.administrador.service.AdministradorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class BootstrapAdministrador implements ApplicationRunner {
    private final AdministradorService service;
    private final RegistrarAdministradorRequest request;

    public BootstrapAdministrador(AdministradorService service,
            @Value("${BOOTSTRAP_ADMIN_EMAIL}") String email,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD}") String password,
            @Value("${BOOTSTRAP_ADMIN_NOMBRE}") String nombre,
            @Value("${BOOTSTRAP_ADMIN_APELLIDO}") String apellido,
            @Value("${BOOTSTRAP_ADMIN_TELEFONO:}") String telefono,
            @Value("${BOOTSTRAP_ADMIN_TELEFONO_LABORAL:}") String telefonoLaboral) {
        this.service = service;
        this.request = new RegistrarAdministradorRequest(email, password, nombre, apellido, telefono, telefonoLaboral);
    }

    @Override
    public void run(ApplicationArguments args) {
        service.registrarInicial(request);
        org.slf4j.LoggerFactory.getLogger(getClass()).info("Administrador inicial disponible; desactivar bootstrap");
    }
}
