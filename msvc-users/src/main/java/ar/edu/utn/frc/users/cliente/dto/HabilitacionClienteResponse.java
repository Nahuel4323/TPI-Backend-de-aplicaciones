package ar.edu.utn.frc.users.cliente.dto;

import java.util.UUID;

public record HabilitacionClienteResponse(UUID clienteId, boolean habilitado) {
}
