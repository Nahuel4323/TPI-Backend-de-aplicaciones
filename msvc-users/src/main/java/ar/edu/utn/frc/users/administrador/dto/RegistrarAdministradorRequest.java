package ar.edu.utn.frc.users.administrador.dto;

import ar.edu.utn.frc.users.shared.keycloak.DatosRegistro;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RegistrarAdministradorRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password,
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @Size(max = 30) String telefono,
        @Size(max = 30) String telefonoLaboral
) implements DatosRegistro {
    @Override
    public String toString() {
        return "RegistrarAdministradorRequest[datos personales y credenciales omitidos]";
    }
}
