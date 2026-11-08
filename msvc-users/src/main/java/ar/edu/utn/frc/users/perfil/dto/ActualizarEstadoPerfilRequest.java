package ar.edu.utn.frc.users.perfil.dto;

import jakarta.validation.constraints.NotNull;

public record ActualizarEstadoPerfilRequest(@NotNull(message = "Debe indicar activo: true o false") Boolean activo) {
}
