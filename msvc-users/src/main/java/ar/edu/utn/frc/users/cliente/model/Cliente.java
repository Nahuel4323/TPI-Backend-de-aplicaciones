package ar.edu.utn.frc.users.cliente.model;

import ar.edu.utn.frc.users.perfil.model.PerfilUsuario;



import ar.edu.utn.frc.users.cliente.enums.CanalContacto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "cliente")
@DiscriminatorValue("CLIENTE")
@PrimaryKeyJoinColumn(name = "usuario_id")
@Getter
@Setter
@NoArgsConstructor
public class Cliente extends PerfilUsuario {

    @Column(name = "fecha_alta", nullable = false, updatable = false)
    private Instant fechaAlta;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferencia_contacto", length = 20)
    private CanalContacto preferenciaContacto;

    @PrePersist
    private void registrarAltaCliente() {
        fechaAlta = Instant.now();
    }
}
