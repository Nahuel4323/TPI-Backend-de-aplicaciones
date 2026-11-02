package ar.edu.utn.frc.users.perfil.repository;


import ar.edu.utn.frc.users.perfil.model.PerfilUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PerfilUsuarioRepository extends JpaRepository<PerfilUsuario, UUID> {

    Optional<PerfilUsuario> findByKeycloakId(String keycloakId);

    boolean existsByKeycloakId(String keycloakId);
}