package com.golivebackend.livekit.service;

/**
 * Thrown when a stream cannot be found during token generation.
 *
 * NOTE: This duplicates the StreamNotFoundException in the stream module.
 * In a stricter modular monolith, both modules would share the exception
 * from common/exception. For now, keeping them separate enforces that
 * the livekit module has no compile-time dependency on stream's internals.
 *
 * UPGRADE PATH:
 * Move to com.golivebackend.common.exception.StreamNotFoundException
 * and have both modules import from there.
 */
public class StreamNotFoundException extends RuntimeException {
    public StreamNotFoundException(String message) {
        super(message);
    }
}