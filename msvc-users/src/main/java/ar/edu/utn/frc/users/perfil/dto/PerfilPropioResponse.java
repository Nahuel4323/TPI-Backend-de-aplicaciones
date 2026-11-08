package ar.edu.utn.frc.users.perfil.dto;

import ar.edu.utn.frc.users.cliente.enums.CanalContacto;

import java.util.UUID;

public record PerfilPropioResponse(
        UUID id,
        String nombre,
        String apellido,
        String telefono,
        String tipoPerfil,
        CanalContacto preferenciaContacto
) {
}