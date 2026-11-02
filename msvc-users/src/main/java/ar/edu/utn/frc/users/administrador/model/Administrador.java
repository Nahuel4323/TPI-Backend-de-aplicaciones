package ar.edu.utn.frc.users.administrador.model;

import ar.edu.utn.frc.users.perfil.model.PerfilUsuario;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "administrador")
@DiscriminatorValue("ADMINISTRADOR")
@PrimaryKeyJoinColumn(name = "usuario_id")
@Getter
@Setter
@NoArgsConstructor
public class Administrador extends PerfilUsuario {

    @Column(name = "fecha_alta", nullable = false, updatable = false)
    private Instant fechaAlta;

    @Column(name = "telefono_laboral", length = 30)
    private String telefonoLaboral;

    @PrePersist
    private void registrarAltaAdministrador() {
        fechaAlta = Instant.now();
    }
}