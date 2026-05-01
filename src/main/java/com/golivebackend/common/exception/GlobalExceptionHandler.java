package com.golivebackend.common.exception;

import com.golivebackend.stream.service.StreamNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Centralised exception handling for all controllers.
 *
 * @RestControllerAdvice intercepts exceptions thrown by any
 * @RestController and routes them here before they reach the client.
 *
 * WITHOUT THIS:
 * Spring Boot returns a generic Whitelabel Error Page or a default
 * JSON error with Hibernate internals exposed — information leakage.
 *
 * WITH THIS:
 * Every error returns a consistent, safe JSON structure.
 * Stack traces never reach the client.
 * HTTP status codes are semantically correct.
 *
 * ERROR RESPONSE SHAPE (consistent across all errors):
 * {
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Stream not found with id: ...",
 *   "timestamp": "2024-01-15T13:30:00Z"
 * }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 404 — Stream not found.
     * Thrown by StreamService when a stream ID doesn't exist in DB.
     */
    @ExceptionHandler(StreamNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleStreamNotFound(
            StreamNotFoundException ex
    ) {
        log.warn("Stream not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * 400 — Validation failure.
     * Thrown when @Valid on a controller parameter fails.
     * E.g. blank title, title too short.
     *
     * We extract all validation messages and return them as a list
     * so the frontend can display each field error specifically.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationFailure(
            MethodArgumentNotValidException ex
    ) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        log.warn("Validation failed: {}", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "status", 400,
                        "error", "Validation Failed",
                        "messages", errors,
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * 409 — Illegal state transition.
     * Thrown by Stream.start() or Stream.end() when the status
     * transition is invalid (e.g. starting an ENDED stream).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex
    ) {
        log.warn("Illegal state: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * 500 — Catch-all for unexpected exceptions.
     * Logs the full stack trace internally.
     * Returns a generic message to the client — no internals exposed.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex
    ) {
        log.error("Unexpected error", ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again."
        );
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "status", status.value(),
                        "error", status.getReasonPhrase(),
                        "message", message,
                        "timestamp", Instant.now().toString()
                ));
    }
}