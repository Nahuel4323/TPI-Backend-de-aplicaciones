package ar.edu.utn.frc.users.cliente.mapper;



import ar.edu.utn.frc.users.cliente.dto.ClienteResponse;
import ar.edu.utn.frc.users.cliente.dto.RegistrarClienteRequest;
import ar.edu.utn.frc.users.cliente.model.Cliente;
import org.springframework.stereotype.Component;

@Component
public class ClienteMapper {

    public Cliente toEntity( RegistrarClienteRequest request, String keycloakId) {
        Cliente cliente = new Cliente();

        cliente.setKeycloakId(keycloakId);
        cliente.setNombre(request.nombre().strip());
        cliente.setApellido(request.apellido().strip());
        cliente.setTelefono(normalizarTelefono(request.telefono()));
        cliente.setPreferenciaContacto(request.preferenciaContacto());

        return cliente;
    }

    public ClienteResponse toResponse(Cliente cliente) {
        return new ClienteResponse(
            cliente.getId(),
            cliente.getNombre(),
            cliente.getApellido(),
            cliente.getTelefono(),
            cliente.getPreferenciaContacto(),
            cliente.isActivo(),
            cliente.getFechaAlta()
        );
    }

    private String normalizarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return null;
        }

        return telefono.strip();
    }
}