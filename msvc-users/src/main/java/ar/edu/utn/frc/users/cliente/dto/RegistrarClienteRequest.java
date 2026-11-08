package ar.edu.utn.frc.users.cliente.dto;

import ar.edu.utn.frc.users.cliente.enums.CanalContacto;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RegistrarClienteRequest(

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    @Size(max = 254)
    String email,

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 12, max = 128, message = "La contraseña debe tener entre 12 y 128 caracteres")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Debe cumplir la política de contraseñas de Keycloak")
    String password,

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre admite hasta 100 caracteres")
    @Schema(example = "Ana", pattern = "\\S")
    String nombre,

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido admite hasta 100 caracteres")
    @Schema(example = "Perez", pattern = "\\S")
    String apellido,

    @Size(max = 30, message = "El teléfono admite hasta 30 caracteres")
    @Schema(example = "3511234567", description = "Obligatorio y no vacío si el canal es TELEFONO")
    String telefono,

    @Schema(example = "EMAIL", description = "Usa el correo registrado; no implica verificación por SMTP")
    CanalContacto preferenciaContacto

) implements ar.edu.utn.frc.users.shared.keycloak.DatosRegistro {

    @Override
    public String toString() {
        return "RegistrarClienteRequest[datos personales y credenciales omitidos]";
    }

    @AssertTrue(
        message = "Debe indicar un teléfono si selecciona contacto telefónico"
    )
    @Schema(hidden = true)
    public boolean isContactoTelefonicoValido() {
        return preferenciaContacto != CanalContacto.TELEFONO
            || (telefono != null && !telefono.isBlank());
    }
}
