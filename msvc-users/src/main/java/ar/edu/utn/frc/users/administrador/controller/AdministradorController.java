package ar.edu.utn.frc.users.administrador.controller;

import ar.edu.utn.frc.users.administrador.dto.AdministradorResponse;
import ar.edu.utn.frc.users.administrador.dto.RegistrarAdministradorRequest;
import ar.edu.utn.frc.users.administrador.service.AdministradorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/administradores")
@RequiredArgsConstructor
public class AdministradorController {
    private final AdministradorService service;

    @Operation(operationId = "listarAdministradores", summary = "Listar administradores y su complejo asignado",
            description = "Requiere ADMIN por el Gateway. Devuelve todos los administradores, incluidos los inactivos. "
                    + "complejoId es null cuando todavía no tienen asignación.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping({"", "/"})
    public ResponseEntity<List<AdministradorResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @Operation(operationId = "consultarAdministrador", summary = "Consultar un administrador",
            description = "ADMIN por Gateway o llamada interna directa a users. Usa el UUID local del perfil.",
            security = @SecurityRequirement(name = "bearerAuth"), responses = {
                @ApiResponse(responseCode = "200", description = "Administrador encontrado"),
                @ApiResponse(responseCode = "400", description = "UUID inválido"),
                @ApiResponse(responseCode = "404", description = "Administrador inexistente")
            })
    @GetMapping({"/{id}", "/{id}/"})
    public ResponseEntity<AdministradorResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.consultar(id));
    }

    @Operation(operationId = "registrarAdministrador", summary = "Registrar un administrador",
            description = "Requiere rol ADMIN en el Gateway. Crea cuenta Keycloak ADMIN sin CLIENTE "
                    + "y perfil administrativo. La asignación a complejo corresponde a US-07. "
                    + "No acepta rol, keycloakId ni complejoId desde este contrato.",
            security = @SecurityRequirement(name = "bearerAuth"), responses = {
                @ApiResponse(responseCode = "201", description = "Administrador creado"),
                @ApiResponse(responseCode = "400", description = "Datos o contraseña inválidos"),
                @ApiResponse(responseCode = "401", description = "Sin autenticación válida en Gateway"),
                @ApiResponse(responseCode = "403", description = "Sin rol ADMIN en Gateway"),
                @ApiResponse(responseCode = "409", description = "Correo o perfil duplicado"),
                @ApiResponse(responseCode = "503", description = "Registro no disponible o compensación pendiente")
            })
    @PostMapping({"", "/"})
    public ResponseEntity<AdministradorResponse> registrar(@Valid @RequestBody RegistrarAdministradorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(request));
    }
}
