package ar.edu.utn.frc.users.perfil.dto;

import ar.edu.utn.frc.users.cliente.enums.CanalContacto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        description = "Campos omitidos o null conservan su valor. Teléfono vacío permite borrarlo salvo que el canal final sea TELEFONO.")
public record ActualizarPerfilRequest(
        @Size(max = 100)
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío")
        String nombre,

        @Size(max = 100)
        @Pattern(regexp = "(?s).*\\S.*", message = "El apellido no puede estar vacío")
        String apellido,

        @Size(max = 30)
        String telefono,

        @Schema(description = "Solo para perfiles CLIENTE")
        CanalContacto preferenciaContacto
) {
}
