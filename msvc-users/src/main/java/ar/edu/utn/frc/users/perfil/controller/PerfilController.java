package ar.edu.utn.frc.users.perfil.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.frc.users.perfil.dto.PerfilPropioResponse;
import ar.edu.utn.frc.users.perfil.dto.ActualizarPerfilRequest;
import ar.edu.utn.frc.users.perfil.service.PerfilService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PatchMapping;


@RestController 
@RequestMapping("/api/users/me")
@RequiredArgsConstructor 
public class PerfilController {

    private final PerfilService perfilService;

    @Operation(
            summary = "Consultar el perfil propio",
            description = "Devuelve el perfil del usuario autenticado por el Gateway.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping
    public ResponseEntity<PerfilPropioResponse> consultarPropio(@RequestHeader(value = "X-User-Id", required = false) String keycloakId){
        return ResponseEntity.ok(perfilService.consultarPropio(keycloakId));
    }

    @Operation(
            operationId = "actualizarPerfilPropio",
            summary = "Actualizar el perfil propio",
            description = "Actualización parcial del perfil identificado por el Gateway. "
                    + "Campos omitidos o null conservan su valor. Rechaza solicitudes sin cambios. "
                    + "Teléfono vacío lo elimina salvo que el canal final sea TELEFONO. "
                    + "La preferencia de contacto solo se admite para clientes. "
                    + "No modifica correo, contraseña, rol, identidad, estado ni asignaciones.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                @ApiResponse(responseCode = "200", description = "Perfil actualizado"),
                @ApiResponse(responseCode = "400", description = "Datos inválidos o cambio no permitido"),
                @ApiResponse(responseCode = "401", description = "Sin identidad autenticada"),
                @ApiResponse(responseCode = "403", description = "Perfil inactivo"),
                @ApiResponse(responseCode = "404", description = "La cuenta no tiene perfil registrado")
            })
    @PatchMapping
    public ResponseEntity<PerfilPropioResponse> actualizarPropio(
            @RequestHeader(value = "X-User-Id", required = false) String keycloakId,
            @Valid @RequestBody ActualizarPerfilRequest request) {
        return ResponseEntity.ok(perfilService.actualizarPropio(keycloakId, request));
    }
    
}
