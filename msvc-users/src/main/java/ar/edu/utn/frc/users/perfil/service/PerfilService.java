package ar.edu.utn.frc.users.perfil.service;


import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ar.edu.utn.frc.users.administrador.model.Administrador;
import ar.edu.utn.frc.users.cliente.model.Cliente;
import ar.edu.utn.frc.users.cliente.enums.CanalContacto;
import ar.edu.utn.frc.users.perfil.dto.ActualizarPerfilRequest;
import ar.edu.utn.frc.users.perfil.dto.ActualizarEstadoPerfilRequest;
import ar.edu.utn.frc.users.perfil.dto.EstadoPerfilResponse;
import ar.edu.utn.frc.users.perfil.dto.PerfilPropioResponse;
import ar.edu.utn.frc.users.perfil.model.PerfilUsuario;
import ar.edu.utn.frc.users.perfil.repository.PerfilUsuarioRepository;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

@Service
@RequiredArgsConstructor 
public class PerfilService {

    private final PerfilUsuarioRepository perfilUsuarioRepository;

    @Transactional
    public EstadoPerfilResponse actualizarEstado(UUID id, ActualizarEstadoPerfilRequest request) {
        var perfil = perfilUsuarioRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "El perfil no existe"));
        perfil.setActivo(request.activo());
        perfilUsuarioRepository.saveAndFlush(perfil);
        return new EstadoPerfilResponse(perfil.getId(),
                perfil instanceof Cliente ? "CLIENTE" : "ADMINISTRADOR", perfil.isActivo());
    }

    @Transactional(readOnly = true) 
    public PerfilPropioResponse consultarPropio(String keycloakId){
        return toResponse(buscarPropioActivo(keycloakId));
    }

    @Transactional
    public PerfilPropioResponse actualizarPropio(String keycloakId, ActualizarPerfilRequest request) {
        PerfilUsuario perfil = buscarPropioActivo(keycloakId);

        if (request.nombre() == null && request.apellido() == null
                && request.telefono() == null && request.preferenciaContacto() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar al menos un cambio");
        }
        if (!(perfil instanceof Cliente) && request.preferenciaContacto() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La preferencia de contacto solo corresponde a clientes");
        }

        String telefono = request.telefono() == null
                ? perfil.getTelefono() : normalizarTelefono(request.telefono());
        if (perfil instanceof Cliente cliente) {
            CanalContacto preferencia = request.preferenciaContacto() == null
                    ? cliente.getPreferenciaContacto() : request.preferenciaContacto();
            if (preferencia == CanalContacto.TELEFONO && (telefono == null || telefono.isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Debe indicar un teléfono para el contacto telefónico");
            }
            cliente.setPreferenciaContacto(preferencia);
        }

        if (request.nombre() != null) perfil.setNombre(request.nombre().strip());
        if (request.apellido() != null) perfil.setApellido(request.apellido().strip());
        perfil.setTelefono(telefono);

        return toResponse(perfilUsuarioRepository.saveAndFlush(perfil));
    }

    private String normalizarTelefono(String telefono) {
        return telefono.isBlank() ? null : telefono.strip();
    }

    private PerfilUsuario buscarPropioActivo(String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) {
          throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No se recibió la identidad del usuario");
        }
        PerfilUsuario perfil = perfilUsuarioRepository.findByKeycloakId(keycloakId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no tiene perfil registrado."));

        if(!perfil.isActivo()){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El perfil esta inactivo");
        }

        return perfil;
    }

    private PerfilPropioResponse toResponse(PerfilUsuario perfil) {
        if(perfil instanceof Cliente cliente){
            return new PerfilPropioResponse(
                cliente.getId(),
                    cliente.getNombre(),
                    cliente.getApellido(),
                    cliente.getTelefono(),
                    "CLIENTE",
                    cliente.getPreferenciaContacto()
            );
        }
        if(perfil instanceof Administrador administrador){
            return new PerfilPropioResponse(
                administrador.getId(),
                administrador.getNombre(),
                    administrador.getApellido(),
                    administrador.getTelefono(),
                    "ADMINISTRADOR",
                    null
            );
        }

        throw new IllegalStateException("Tipo de perfil no soportado");
    }
    
}
