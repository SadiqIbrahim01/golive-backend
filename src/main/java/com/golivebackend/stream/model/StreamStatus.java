package com.golivebackend.stream.model;

/**
 * Represents the lifecycle state of a stream.
 *
 * WHY AN ENUM AND NOT A STRING?
 * Storing status as a plain String ("LIVE", "ENDED") in the DB is common
 * but dangerous:
 *   - A typo ("LIVEE") is valid at compile time but broken at runtime
 *   - No compiler enforcement of valid transitions
 *   - IDE can't autocomplete or refactor across usages
 *
 * An enum gives us:
 *   - Compile-time safety — StreamStatus.LIVEE won't compile
 *   - Type-safe switch expressions (Java 21)
 *   - A single source of truth for valid states
 *
 * LIFECYCLE:
 *   CREATED ──► LIVE ──► ENDED
 *
 * CREATED → LIVE:   host calls PATCH /api/streams/{id}/start
 * LIVE    → ENDED:  host calls PATCH /api/streams/{id}/end
 *
 * There is no going back. Once ENDED, a stream is immutable.
 * If a host wants to stream again, they create a new stream.
 */
public enum StreamStatus {

    /**
     * Stream has been created. Host has URLs.
     * No media is flowing yet. Room exists logically, not in LiveKit.
     */
    CREATED,

    /**
     * Host has joined LiveKit and started screen sharing.
     * Viewers can join and watch.
     */
    LIVE,

    /**
     * Stream is over. Immutable. Will not appear in live listings.
     */
    ENDED
}