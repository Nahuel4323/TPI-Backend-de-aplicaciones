package ar.edu.utn.frc.users.cliente.service;

import ar.edu.utn.frc.users.cliente.dto.ClienteResponse;
import ar.edu.utn.frc.users.cliente.dto.RegistrarClienteRequest;
import ar.edu.utn.frc.users.cliente.enums.CanalContacto;
import ar.edu.utn.frc.users.cliente.mapper.ClienteMapper;
import ar.edu.utn.frc.users.cliente.repository.ClienteRepository;
import ar.edu.utn.frc.users.perfil.repository.PerfilUsuarioRepository;
import ar.edu.utn.frc.users.shared.exception.PerfilYaRegistradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final PerfilUsuarioRepository perfilUsuarioRepository;
    private final ClienteMapper clienteMapper;

    @Transactional
    public ClienteResponse registrar(@NotBlank @Size(max = 255) String keycloakId, boolean tieneCorreo, @NotNull @Valid RegistrarClienteRequest request) {
        if (perfilUsuarioRepository.existsByKeycloakId(keycloakId)) {
            throw new PerfilYaRegistradoException();
        }
        if (request.preferenciaContacto() == CanalContacto.EMAIL && !tieneCorreo) {
            throw new IllegalArgumentException("La cuenta de Keycloak debe tener correo para seleccionar EMAIL");
        }
        var cliente = clienteMapper.toEntity(request, keycloakId);
        return clienteMapper.toResponse(clienteRepository.saveAndFlush(cliente));
    }
}
