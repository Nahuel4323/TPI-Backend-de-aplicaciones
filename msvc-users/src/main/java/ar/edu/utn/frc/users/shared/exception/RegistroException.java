package ar.edu.utn.frc.users.shared.exception;

import org.springframework.http.HttpStatus;

public class RegistroException extends RuntimeException {
    private final HttpStatus status;

    public RegistroException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
