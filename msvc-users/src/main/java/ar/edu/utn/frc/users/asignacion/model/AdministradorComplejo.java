package ar.edu.utn.frc.users.asignacion.model;

import ar.edu.utn.frc.users.administrador.model.Administrador;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "administrador_complejo")
@Getter
@Setter
@NoArgsConstructor
public class AdministradorComplejo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "administrador_id",
        nullable = false,
        unique = true
    )
    private Administrador administrador;

    // Identificador del complejo que pertenece a msvc-spaces.
    @Column(name = "complejo_id", nullable = false)
    private UUID complejoId;

    @Column(name = "asignado_en", nullable = false, updatable = false)
    private Instant asignadoEn;

    @PrePersist
    private void registrarAsignacion() {
        asignadoEn = Instant.now();
    }
}