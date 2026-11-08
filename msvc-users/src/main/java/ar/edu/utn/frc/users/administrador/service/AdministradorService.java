package ar.edu.utn.frc.users.administrador.service;

import ar.edu.utn.frc.users.administrador.dto.AdministradorResponse;
import ar.edu.utn.frc.users.administrador.dto.RegistrarAdministradorRequest;
import ar.edu.utn.frc.users.administrador.mapper.AdministradorMapper;
import ar.edu.utn.frc.users.administrador.repository.AdministradorRepository;
import ar.edu.utn.frc.users.asignacion.repository.AdministradorComplejoRepository;
import ar.edu.utn.frc.users.perfil.repository.PerfilUsuarioRepository;
import ar.edu.utn.frc.users.shared.exception.PerfilYaRegistradoException;
import ar.edu.utn.frc.users.shared.exception.RegistroException;
import ar.edu.utn.frc.users.shared.keycloak.KeycloakRegistrationClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
public class AdministradorService {
    private final AdministradorRepository administradores;
    private final PerfilUsuarioRepository perfiles;
    private final AdministradorMapper mapper;
    private final KeycloakRegistrationClient keycloak;
    private final TransactionTemplate transaction;
    private final AdministradorComplejoRepository asignaciones;

    @Transactional(readOnly = true)
    public List<AdministradorResponse> listar() {
        return administradores.findAll(Sort.by("apellido", "nombre", "id")).stream()
                .map(admin -> mapper.toResponse(admin, asignaciones.findByAdministrador_Id(admin.getId())
                        .map(asignacion -> asignacion.getComplejoId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdministradorResponse consultar(UUID id) {
        var admin = administradores.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "El administrador no existe"));
        return mapper.toResponse(admin, asignaciones.findByAdministrador_Id(id)
                .map(asignacion -> asignacion.getComplejoId()).orElse(null));
    }

    public AdministradorResponse registrar(@NotNull @Valid RegistrarAdministradorRequest request) {
        String token = keycloak.token();
        String id = keycloak.crearAdministradorDeshabilitado(token, request);
        try {
            return transaction.execute(status -> {
                if (perfiles.existsByKeycloakId(id)) throw new PerfilYaRegistradoException();
                var response = mapper.toResponse(administradores.saveAndFlush(mapper.toEntity(request, id)));
                keycloak.habilitarCuenta(token, id);
                return response;
            });
        } catch (RuntimeException ex) {
            keycloak.eliminarCuenta(token, id);
            throw ex;
        }
    }

    /** Solo se invoca desde el arranque local explícito, nunca desde un endpoint público. */
    public AdministradorResponse registrarInicial(@NotNull @Valid RegistrarAdministradorRequest request) {
        if (administradores.count() > 0) {
            String id = keycloak.buscarAdministradorExistente(keycloak.token(), request.email());
            if (id != null) {
                var existing = administradores.findByKeycloakId(id);
                if (existing.isPresent() && existing.get().isActivo()) return mapper.toResponse(existing.get());
            }
            throw new RegistroException(HttpStatus.CONFLICT,
                    "Ya hay administradores; usar el alta autenticada por el Gateway");
        }
        return registrar(request);
    }
}
