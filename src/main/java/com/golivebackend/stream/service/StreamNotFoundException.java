package com.golivebackend.stream.service;

/**
 * Thrown when a stream cannot be found by the given identifier.
 *
 * WHY A CUSTOM EXCEPTION?
 * We could throw IllegalArgumentException or use Optional downstream.
 * A named exception gives us:
 *   - Semantic clarity: "stream not found" vs generic "illegal argument"
 *   - A single place to map to HTTP 404 in the exception handler
 *   - Searchability: grep for StreamNotFoundException shows every
 *     place in the codebase where this failure is possible
 *
 * RuntimeException (unchecked): callers don't have to declare it
 * with 'throws'. The exception handler catches it at the boundary.
 */
public class StreamNotFoundException extends RuntimeException {

    public StreamNotFoundException(String message) {
        super(message);
    }
}