package ar.edu.utn.frc.users.shared.keycloak;

/** Datos de identidad compartidos por las altas de cliente y administrador. */
public interface DatosRegistro {
    String email();
    String password();
    String nombre();
    String apellido();
}
