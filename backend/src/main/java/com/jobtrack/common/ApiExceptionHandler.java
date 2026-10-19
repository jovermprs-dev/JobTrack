package com.jobtrack.common;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Respuestas de error uniformes en formato RFC 9457 (problem+json). */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record FieldProblem(String field, String message) {
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldProblem(e.getField(), e.getDefaultMessage()))
                .toList();
        List<FieldProblem> globals = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> new FieldProblem(e.getObjectName(), e.getDefaultMessage()))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos.");
        problem.setTitle("Validación fallida");
        problem.setProperty("errors", java.util.stream.Stream.concat(errors.stream(), globals.stream()).toList());
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos.");
        problem.setTitle("Validación fallida");
        problem.setProperty("errors", ex.getConstraintViolations().stream()
                .map(v -> new FieldProblem(v.getPropertyPath().toString(), v.getMessage())).toList());
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handleBadRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Petición no válida", ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas", "Email o contraseña incorrectos.");
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    ResponseEntity<ProblemDetail> handleTooMany(TooManyAttemptsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos", ex.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).headers(headers)
                .body(problem(HttpStatus.CONTENT_TOO_LARGE, "Archivo demasiado grande", "El archivo supera el tamaño máximo de 2 MB."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflicto de datos", "La operación entra en conflicto con los datos existentes.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Se ha producido un error inesperado.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
