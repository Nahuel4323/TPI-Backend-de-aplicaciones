package ar.edu.utn.frc.users.asignacion.repository;



import ar.edu.utn.frc.users.asignacion.model.AdministradorComplejo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdministradorComplejoRepository extends JpaRepository<AdministradorComplejo, UUID> {

    Optional<AdministradorComplejo> findByAdministrador_Id(
        UUID administradorId
    );

    boolean existsByAdministrador_Id(UUID administradorId);

    List<AdministradorComplejo> findAllByComplejoId(UUID complejoId);

    boolean existsByAdministrador_IdAndComplejoId(
        UUID administradorId,
        UUID complejoId
    );
}
