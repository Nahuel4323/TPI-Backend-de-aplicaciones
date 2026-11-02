package ar.edu.utn.frc.users.shared.exception;



public class PerfilYaRegistradoException extends RuntimeException {

    public PerfilYaRegistradoException() {
        super("La cuenta ya tiene un perfil registrado");
    }
}
