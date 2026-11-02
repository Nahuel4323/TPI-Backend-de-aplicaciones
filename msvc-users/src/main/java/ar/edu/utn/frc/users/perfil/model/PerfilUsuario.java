package ar.edu.utn.frc.users.perfil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "perfil_usuario")
@Inheritance(strategy = InheritanceType.JOINED) // Los datos se reparten entre la tabla padre y la tabla del subtipo
@DiscriminatorColumn(
    name = "tipo_perfil",
    discriminatorType = DiscriminatorType.STRING,
    length = 20
)
@Getter
@Setter
@NoArgsConstructor
public abstract class PerfilUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true, length = 255)
    private String keycloakId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(length = 30)
    private String telefono;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    protected void registrarCreacion() {
        Instant ahora = Instant.now();
        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    protected void registrarActualizacion() {
        actualizadoEn = Instant.now();
    }
}