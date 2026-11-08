package ar.edu.utn.frc.users.cliente.service;

import ar.edu.utn.frc.users.cliente.dto.ClienteResponse;
import ar.edu.utn.frc.users.cliente.dto.RegistrarClienteRequest;
import ar.edu.utn.frc.users.cliente.dto.HabilitacionClienteResponse;
import ar.edu.utn.frc.users.cliente.model.Cliente;
import ar.edu.utn.frc.users.cliente.mapper.ClienteMapper;
import ar.edu.utn.frc.users.cliente.repository.ClienteRepository;
import ar.edu.utn.frc.users.perfil.repository.PerfilUsuarioRepository;
import ar.edu.utn.frc.users.shared.exception.PerfilYaRegistradoException;
import ar.edu.utn.frc.users.shared.keycloak.KeycloakRegistrationClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final PerfilUsuarioRepository perfilUsuarioRepository;
    private final ClienteMapper clienteMapper;
    private final KeycloakRegistrationClient keycloak;
    private final TransactionTemplate transaction;

    @Transactional(readOnly = true)
    public List<ClienteResponse> buscar(String nombre, Boolean activo) {
        if (nombre != null && nombre.length() > 201) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El filtro nombre admite hasta 201 caracteres");
        }
        String patron = nombre == null || nombre.isBlank() ? null
                : "%" + nombre.strip().toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Specification<Cliente> filtros = (root, query, cb) -> {
            var condiciones = new ArrayList<Predicate>();
            if (patron != null) {
                var nombreCompleto = cb.concat(cb.concat(root.<String>get("nombre"), " "), root.<String>get("apellido"));
                condiciones.add(cb.like(cb.lower(nombreCompleto), patron, '!'));
            }
            if (activo != null) condiciones.add(cb.equal(root.get("activo"), activo));
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return clienteRepository.findAll(filtros, Sort.by("apellido", "nombre", "id"))
                .stream().map(clienteMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClienteResponse consultar(UUID id) {
        return clienteMapper.toResponse(buscarCliente(id));
    }

    @Transactional(readOnly = true)
    public HabilitacionClienteResponse consultarHabilitacion(UUID id) {
        var cliente = buscarCliente(id);
        return new HabilitacionClienteResponse(cliente.getId(), cliente.isActivo());
    }

    private Cliente buscarCliente(UUID id) {
        return clienteRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "El cliente no existe"));
    }

    public ClienteResponse registrar(@NotNull @Valid RegistrarClienteRequest request) {
        String token = keycloak.token();
        String keycloakId = keycloak.crearCuentaDeshabilitada(token, request);
        try {
            // El commit también queda dentro del try: si falla, compensar el alta externa.
            return transaction.execute(status -> {
                if (perfilUsuarioRepository.existsByKeycloakId(keycloakId)) {
                    throw new PerfilYaRegistradoException();
                }
                var cliente = clienteMapper.toEntity(request, keycloakId);
                var response = clienteMapper.toResponse(clienteRepository.saveAndFlush(cliente));
                keycloak.habilitarCuenta(token, keycloakId);
                return response;
            });
        } catch (RuntimeException ex) {
            keycloak.eliminarCuenta(token, keycloakId);
            throw ex;
        }
    }
}
