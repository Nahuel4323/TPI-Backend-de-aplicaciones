package ar.edu.utn.frc.users.cliente.repository;



import ar.edu.utn.frc.users.cliente.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    Optional<Cliente> findByKeycloakId(String keycloakId);
}