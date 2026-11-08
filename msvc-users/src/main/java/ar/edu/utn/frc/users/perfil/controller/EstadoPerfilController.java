package ar.edu.utn.frc.users.perfil.controller;

import ar.edu.utn.frc.users.perfil.dto.ActualizarEstadoPerfilRequest;
import ar.edu.utn.frc.users.perfil.dto.EstadoPerfilResponse;
import ar.edu.utn.frc.users.perfil.service.PerfilService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class EstadoPerfilController {
    private final PerfilService service;

    @Operation(operationId = "actualizarEstadoPerfil", summary = "Activar o desactivar un perfil",
            description = "Requiere ADMIN por Gateway. Cambia el estado lógico del perfil local, "
                    + "sin borrar su información ni asignaciones. No cambia las credenciales o roles de Keycloak.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping({"/{id}/estado", "/{id}/estado/"})
    public ResponseEntity<EstadoPerfilResponse> actualizarEstado(
            @PathVariable UUID id, @Valid @RequestBody ActualizarEstadoPerfilRequest request) {
        return ResponseEntity.ok(service.actualizarEstado(id, request));
    }
}
