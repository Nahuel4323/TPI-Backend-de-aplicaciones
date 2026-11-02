package ar.edu.utn.frc.users.shared.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PerfilYaRegistradoException.class)
    ProblemDetail perfilExistente(PerfilYaRegistradoException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail datosInvalidos(MethodArgumentNotValidException ex) {
        var errors = new LinkedHashMap<String, String>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        var detail = problem(HttpStatus.BAD_REQUEST, "Revisar los datos del registro");
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail jsonInvalido() {
        return problem(HttpStatus.BAD_REQUEST, "JSON invalido, campo desconocido o canal de contacto no permitido");
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    ProblemDetail solicitudInvalida(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Datos de registro invalidos o correo de Keycloak ausente");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflictoDePersistencia() {
        // Incluye la carrera entre dos altas del mismo keycloak_id. No exponer SQL.
        return problem(HttpStatus.CONFLICT, "El perfil ya existe o los datos entran en conflicto con un registro existente");
    }

    private ProblemDetail problem(HttpStatus status, String message) {
        var detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        return detail;
    }
}
