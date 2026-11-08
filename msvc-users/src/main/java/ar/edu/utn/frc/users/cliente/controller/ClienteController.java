package ar.edu.utn.frc.users.cliente.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import ar.edu.utn.frc.users.cliente.dto.ClienteResponse;
import ar.edu.utn.frc.users.cliente.dto.RegistrarClienteRequest;
import ar.edu.utn.frc.users.cliente.dto.HabilitacionClienteResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ar.edu.utn.frc.users.cliente.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/users/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @Operation(operationId = "buscarClientes", summary = "Buscar clientes por nombre o estado",
            description = "ADMIN por el Gateway. nombre busca una coincidencia parcial en nombre y apellido "
                    + "sin distinguir mayúsculas; activo filtra true/false. Se pueden combinar. "
                    + "Devuelve la lista completa de coincidencias. Sin filtros incluye ambos estados.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping({"", "/"})
    public ResponseEntity<List<ClienteResponse>> buscar(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(clienteService.buscar(nombre, activo));
    }

    @Operation(operationId = "consultarCliente", summary = "Consultar los datos de un cliente",
            description = "ADMIN por el Gateway o llamada interna directa a users. "
                    + "El id es el UUID local del perfil, no el identificador de Keycloak.",
            security = @SecurityRequirement(name = "bearerAuth"), responses = {
                @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
                @ApiResponse(responseCode = "400", description = "UUID inválido"),
                @ApiResponse(responseCode = "404", description = "Cliente inexistente")
            })
    @GetMapping({"/{id}", "/{id}/"})
    public ResponseEntity<ClienteResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(clienteService.consultar(id));
    }

    @Operation(operationId = "consultarHabilitacionCliente", summary = "Consultar habilitación para reservar",
            description = "Solo canal interno, directo a users. El Gateway público bloquea esta ruta. "
                    + "Un cliente activo devuelve habilitado=true; uno inactivo devuelve false; "
                    + "un UUID sin cliente devuelve 404. Se usa el UUID local.", security = {}, responses = {
                @ApiResponse(responseCode = "200", description = "Estado de habilitación"),
                @ApiResponse(responseCode = "400", description = "UUID inválido"),
                @ApiResponse(responseCode = "404", description = "Cliente inexistente")
            })
    @GetMapping({"/{id}/habilitacion", "/{id}/habilitacion/"})
    public ResponseEntity<HabilitacionClienteResponse> consultarHabilitacion(@PathVariable UUID id) {
        return ResponseEntity.ok(clienteService.consultarHabilitacion(id));
    }

    @Operation(
        operationId = "registrarCliente", summary = "Registrar una cuenta y un perfil de cliente",
        description = "Público, sin JWT previo. Recibe email, password y datos personales. "
            + "Crea la cuenta en Keycloak con rol CLIENTE y el perfil vinculado. "
            + "No acepta roles ni keycloakId. No guarda ni devuelve la contraseña.",
        security = {},
        responses = {
            @ApiResponse(responseCode = "201", description = "Perfil creado",
                content = @Content(schema = @Schema(implementation = ClienteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, propiedades desconocidas o contacto sin datos", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Correo ya registrado o conflicto de persistencia", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Identidad o persistencia no disponible; el registro no se completó", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
        })
    @PostMapping({"", "/"})
    public ResponseEntity<ClienteResponse> registrar(@Valid @RequestBody RegistrarClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clienteService.registrar(request));
    }
}
