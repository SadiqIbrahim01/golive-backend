package com.golivebackend.stream.model;

/**
 * The type of media a host is sharing in this stream.
 *
 * Stored in the DB and returned in responses so the frontend
 * can render the correct UI (screen share layout vs camera layout).
 *
 * LiveKit handles both types at the media level —
 * this field is purely metadata for display purposes.
 */
public enum StreamType {

    /** Host is sharing their screen or a specific application window. */
    SCREEN_SHARE,

    /** Host is sharing their camera feed. */
    CAMERA
}