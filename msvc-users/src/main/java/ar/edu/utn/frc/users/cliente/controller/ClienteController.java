package ar.edu.utn.frc.users.cliente.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import ar.edu.utn.frc.users.cliente.dto.ClienteResponse;
import ar.edu.utn.frc.users.cliente.dto.RegistrarClienteRequest;
import ar.edu.utn.frc.users.cliente.service.ClienteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @Operation(
        operationId = "registrarCliente", summary = "Completar el perfil de la cuenta autenticada",
        description = "Requiere CLIENTE sin ADMIN, emisor del realm y audiencia backend-api. "
            + "La identidad proviene del token: no enviar roles, keycloakId, correo ni contraseña. "
            + "Cada cuenta admite un solo perfil. Si falla, conserva su cuenta y puede reintentar.",
        security = @SecurityRequirement(name = "bearerAuth"),
        responses = {
            @ApiResponse(responseCode = "201", description = "Perfil creado",
                content = @Content(schema = @Schema(implementation = ClienteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, propiedades desconocidas o contacto sin datos", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido, vencido, emisor o audiencia incorrectos", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin CLIENTE, con rol administrativo o acceso directo a users", content = @Content),
            @ApiResponse(responseCode = "409", description = "La cuenta ya tiene perfil o conflicto de persistencia", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
        })
    @PostMapping
    public ResponseEntity<ClienteResponse> registrar(
            @Valid @RequestBody RegistrarClienteRequest request,
            HttpServletRequest servletRequest) {
        // El filtro interno garantiza el origen antes de confiar en estos datos.
        String keycloakId = servletRequest.getHeader("X-User-Id");
        boolean tieneCorreo = servletRequest.getHeader("X-User-Email") != null
                && !servletRequest.getHeader("X-User-Email").isBlank();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clienteService.registrar(keycloakId, tieneCorreo, request));
    }
}
