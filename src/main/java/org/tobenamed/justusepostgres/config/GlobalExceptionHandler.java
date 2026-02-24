package org.tobenamed.justusepostgres.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;

/**
 * Centralized exception handling for all REST controllers.
 * Returns RFC 7807 Problem Detail JSON instead of raw stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ProblemDetail handleNotFound(EmptyResultDataAccessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "The requested resource was not found.");
        problem.setTitle("Not Found");
        problem.setType(URI.create("https://problems.example.com/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://problems.example.com/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing.");
        problem.setTitle("Missing Parameter");
        problem.setType(URI.create("https://problems.example.com/missing-parameter"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("parameter", ex.getParameterName());
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' should be of type "
                        + (ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown") + ".");
        problem.setTitle("Type Mismatch");
        problem.setType(URI.create("https://problems.example.com/type-mismatch"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDatabaseError(DataAccessException ex) {
        log.error("Database error: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "A database error occurred. Check server logs for details.");
        problem.setTitle("Database Error");
        problem.setType(URI.create("https://problems.example.com/database-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Check server logs for details.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://problems.example.com/internal-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
