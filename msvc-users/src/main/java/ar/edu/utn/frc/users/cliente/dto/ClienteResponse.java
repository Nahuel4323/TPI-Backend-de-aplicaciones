package ar.edu.utn.frc.users.cliente.dto;

import ar.edu.utn.frc.users.cliente.enums.CanalContacto;

import java.time.Instant;
import java.util.UUID;

public record ClienteResponse(
    UUID id,
    String nombre,
    String apellido,
    String telefono,
    CanalContacto preferenciaContacto,
    boolean activo,
    Instant fechaAlta
) {
}