package com.golivebackend.livekit.service;

/**
 * Thrown when a HOST token is requested with an invalid or missing hostKey.
 *
 * Mapped to HTTP 403 Forbidden by the GlobalExceptionHandler.
 * We use a dedicated exception (not IllegalArgumentException) so the
 * handler can return exactly 403 — distinguishing auth failure from
 * bad input (400) cleanly.
 */
public class UnauthorisedHostException extends RuntimeException {
    public UnauthorisedHostException(String message) {
        super(message);
    }
}