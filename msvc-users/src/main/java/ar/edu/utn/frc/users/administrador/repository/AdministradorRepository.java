package ar.edu.utn.frc.users.administrador.repository;



import ar.edu.utn.frc.users.administrador.model.Administrador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdministradorRepository extends JpaRepository<Administrador, UUID> {

    Optional<Administrador> findByKeycloakId(String keycloakId);
}
