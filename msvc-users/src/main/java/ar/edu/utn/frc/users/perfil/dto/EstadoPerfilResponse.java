package ar.edu.utn.frc.users.perfil.dto;

import java.util.UUID;

public record EstadoPerfilResponse(UUID id, String tipoPerfil, boolean activo) {
}
