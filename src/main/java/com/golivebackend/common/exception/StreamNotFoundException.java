package com.golivebackend.common.exception;

/**
 * Thrown when a stream cannot be found by the given identifier.
 *
 * Lives in common/exception so both the stream module and the
 * livekit module can throw it without either depending on the other.
 *
 * This is the correct modular monolith pattern:
 *   shared concepts → common
 *   module-specific concepts → stay in the module
 *
 * StreamNotFoundException is shared — both modules need it.
 * UnauthorisedHostException is livekit-specific — stays there.
 */
public class StreamNotFoundException extends RuntimeException {

    public StreamNotFoundException(String message) {
        super(message);
    }
}