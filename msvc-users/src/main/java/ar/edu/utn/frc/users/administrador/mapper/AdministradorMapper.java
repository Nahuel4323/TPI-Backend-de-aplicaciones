package ar.edu.utn.frc.users.administrador.mapper;

import ar.edu.utn.frc.users.administrador.dto.AdministradorResponse;
import ar.edu.utn.frc.users.administrador.dto.RegistrarAdministradorRequest;
import ar.edu.utn.frc.users.administrador.model.Administrador;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class AdministradorMapper {
    public Administrador toEntity(RegistrarAdministradorRequest request, String keycloakId) {
        var admin = new Administrador();
        admin.setKeycloakId(keycloakId);
        admin.setNombre(request.nombre().strip());
        admin.setApellido(request.apellido().strip());
        admin.setTelefono(normalizar(request.telefono()));
        admin.setTelefonoLaboral(normalizar(request.telefonoLaboral()));
        return admin;
    }

    public AdministradorResponse toResponse(Administrador admin) {
        return toResponse(admin, null);
    }

    public AdministradorResponse toResponse(Administrador admin, UUID complejoId) {
        return new AdministradorResponse(admin.getId(), admin.getNombre(), admin.getApellido(),
                admin.getTelefono(), admin.getTelefonoLaboral(), admin.isActivo(), admin.getFechaAlta(), complejoId);
    }

    private String normalizar(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
