package com.facilityhub.api;

import com.facilityhub.service.ConflictoCodificadoException;
import com.facilityhub.service.ConflictoException;
import com.facilityhub.service.RecursoNoDisponibleException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import com.facilityhub.service.RecursoNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail notFound(RecursoNoEncontradoException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Recurso no encontrado");
        return detail;
    }

    @ExceptionHandler(RecursoNoDisponibleException.class)
    public ProblemDetail noDisponible(RecursoNoDisponibleException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recurso no encontrado");
        detail.setTitle("Recurso no encontrado");
        return detail;
    }

    // FH-006: conflictos de estado, de unicidad (p. ej. dos asignaciones activas) o de version concurrente.
    // El detalle de integridad no se expone: puede contener nombres internos de restricciones o valores.
    // FH-012: conflicto con codigo estable y datos no sensibles (p. ej. obligaciones afectadas).
    @ExceptionHandler(ConflictoCodificadoException.class)
    public ProblemDetail conflictoCodificado(ConflictoCodificadoException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Conflicto");
        detail.setProperty("codigo", ex.getCodigo());
        ex.getDatos().forEach(detail::setProperty);
        return detail;
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail conflicto(ConflictoException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Conflicto");
        return detail;
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ProblemDetail conflictoPersistencia(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "El recurso fue modificado o la operacion entra en conflicto con su estado actual");
        detail.setTitle("Conflicto");
        return detail;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden(AccessDeniedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Operacion no permitida");
        detail.setTitle("Acceso denegado");
        return detail;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail unauthorized(AuthenticationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales no validas");
        detail.setTitle("No autorizado");
        return detail;
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ProblemDetail badRequest(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Peticion no valida");
        return detail;
    }
}
