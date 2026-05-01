package com.golivebackend.common.exception;

import com.golivebackend.livekit.service.UnauthorisedHostException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 404 — Stream not found.
     * Now catches the single shared StreamNotFoundException from common.
     * No ambiguity — one exception class, one handler.
     */
    @ExceptionHandler(StreamNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleStreamNotFound(
            StreamNotFoundException ex
    ) {
        log.warn("Stream not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * 403 — Invalid or missing host key.
     */
    @ExceptionHandler(UnauthorisedHostException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorisedHost(
            UnauthorisedHostException ex
    ) {
        log.warn("Unauthorised host attempt: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * 400 — Bean Validation failure (@Valid on controller params).
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
     * 409 — Illegal stream state transition.
     * Thrown by Stream.start() or Stream.end() domain methods.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex
    ) {
        log.warn("Illegal state transition: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * 500 — Catch-all for anything unexpected.
     * Stack trace logged internally. Generic message to client.
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