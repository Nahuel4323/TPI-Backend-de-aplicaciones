package ar.edu.utn.frc.users.administrador.dto;

import java.time.Instant;
import java.util.UUID;

public record AdministradorResponse(UUID id, String nombre, String apellido,
        String telefono, String telefonoLaboral, boolean activo, Instant fechaAlta, UUID complejoId) {
}
