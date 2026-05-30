package com.golivebackend.stream.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Core domain entity representing a GoLive stream session.
 *
 * MAPPING DECISIONS:
 *
 * @Entity    → Hibernate manages this class as a DB table
 * @Table     → explicit table name (don't rely on Hibernate's default
 *              naming — it changes with config and causes surprises)
 *
 * LOMBOK ANNOTATIONS:
 * @Getter          → generates getters for all fields
 * @Builder         → generates a type-safe builder (Stream.builder()...build())
 * @NoArgsConstructor → Hibernate requires a no-arg constructor to
 *                      instantiate entities via reflection
 * @AllArgsConstructor → required by @Builder when @NoArgsConstructor is present
 *
 * WHY NO @SETTER?
 * We do NOT generate setters on the entity. Entities should be mutated
 * only through meaningful domain methods (start(), end()) — not through
 * arbitrary setField() calls scattered across the codebase.
 * This enforces that all state transitions go through one place.
 */
@Entity
@Table(name = "streams")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stream {

    /**
     * PRIMARY KEY
     *
     * @Id marks this as the primary key.
     *
     * @GeneratedValue(strategy = GenerationType.AUTO) with a UUID type
     * tells Hibernate to use UUID generation. In Hibernate 6+ (Spring Boot 3+),
     * this uses a UUID generator automatically when the field type is UUID.
     *
     * WHY NOT @GeneratedValue(strategy = IDENTITY)?
     * IDENTITY uses PostgreSQL's SERIAL — auto-increment Long.
     * We explicitly want UUID for the reasons discussed in the design section.
     *
     * WHY NOT @UuidGenerator(style = TIME)?
     * That would give us UUID v7 (time-ordered). Ideal, but requires
     * Hibernate 6.2+. We're noting it here as the upgrade path.
     * For now, UUID v4 (random) from @GeneratedValue is correct.
     *
     * @Column(updatable = false) → once set, the PK never changes.
     * Hibernate enforces this — any attempt to update it throws an exception.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
            name = "stream_id",
            updatable = false,
            nullable = false
    )
    private UUID streamId;

    /**
     * ROOM NAME
     *
     * The identifier sent to LiveKit that groups host and viewer
     * into the same media session.
     *
     * This is different from streamId because:
     *   - streamId is our internal DB key
     *   - roomName is what LiveKit uses — it can follow its own
     *     format requirements (no hyphens in some SDK versions)
     *
     * We generate this as "room-{UUID}" in the service layer.
     * Stored as a unique string — two streams must never share a roomName
     * or their media sessions would be mixed together.
     *
     * unique = true → enforced at DB level, not just application level.
     * Always enforce uniqueness constraints at the DB level.
     * Application-level checks alone have race condition vulnerabilities.
     */
    @Column(
            name = "room_name",
            nullable = false,
            unique = true,
            updatable = false
    )
    private String roomName;

    /**
     * HOST KEY
     *
     * A secret token that proves a request comes from the legitimate host.
     * Think of it as a single-use API key for this stream.
     *
     * SECURITY PROPERTIES:
     *   - Generated as a cryptographically random UUID (not sequential)
     *   - Returned to the host ONCE on stream creation
     *   - Never returned again after that initial response
     *   - Required on PATCH /start and PATCH /end calls
     *   - Never exposed in watch URLs or viewer responses
     *
     * WHY NOT HASH IT?
     * In a full auth system with user accounts, you'd hash this like a
     * password (bcrypt). For this MVP without user accounts, storing it
     * plaintext is an acceptable trade-off — noted as a production upgrade.
     *
     * column definition is 'TEXT' — UUIDs as strings are 36 chars but
     * we use TEXT for flexibility if we ever change the format.
     */
    @Column(
            name = "host_key",
            nullable = false,
            updatable = false
    )
    private String hostKey;

    /**
     * TITLE
     *
     * Human-readable name for the stream.
     * Optional — we'll default to "Untitled Stream" in the service if blank.
     *
     * length = 255 is the PostgreSQL VARCHAR default and enough for a title.
     */
    @Column(
            name = "title",
            nullable = false,
            length = 255
    )
    private String title;

    /**
     * STATUS
     *
     * @Enumerated(EnumType.STRING):
     * Stores the enum name as a string in the DB ("CREATED", "LIVE", "ENDED").
     *
     * WHY NOT EnumType.ORDINAL?
     * ORDINAL stores the enum's position (0, 1, 2).
     * If you ever reorder the enum values, existing DB rows
     * silently map to the wrong status.
     * ORDINAL is the default and it is a trap. Always use STRING.
     */

    /**
     * The category this stream belongs to.
     * Free-text — "Gaming", "Education", "Music", etc.
     * Nullable for backwards compatibility with existing streams.
     */
    @Column(
            name = "category",
            length = 100
    )
    private String category;

    /**
     * The type of media being shared.
     * Nullable — existing streams created before this field existed
     * will have null here. Frontend should default to SCREEN_SHARE.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "stream_type",
            length = 20
    )
    private StreamType streamType;

    /**
     * Current number of active viewers.
     * Incremented via WebSocket SessionSubscribeEvent.
     * Decremented via WebSocket SessionDisconnectEvent.
     * Never goes below zero — enforced by GREATEST() in atomic query.
     */
    @Column(
            name = "viewer_count",
            nullable = false
    )
    @Builder.Default
    private int viewerCount = 0;

    /**
     * Total likes from unique sessions.
     * Incremented atomically when a new session likes this stream.
     * Decremented atomically when a session unlikes.
     * Enforced unique per (sessionId, streamId) in stream_likes table.
     */
    @Column(
            name = "likes_count",
            nullable = false
    )
    @Builder.Default
    private int likesCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private StreamStatus status;

    /**
     * TIMESTAMPS
     *
     * All stored as Instant (UTC).
     * createdAt: set once on creation, never changes.
     * startedAt: set when status transitions to LIVE.
     * endedAt:   set when status transitions to ENDED.
     *
     * startedAt and endedAt are nullable — a CREATED stream
     * has neither yet.
     *
     * columnDefinition = "TIMESTAMPTZ" explicitly tells PostgreSQL
     * to use TIMESTAMP WITH TIME ZONE — the correct type for Instant.
     * Without this, Hibernate may use TIMESTAMP (no timezone) which
     * loses timezone information at the DB level.
     */
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "TIMESTAMPTZ"
    )
    private Instant createdAt;

    @Column(
            name = "started_at",
            columnDefinition = "TIMESTAMPTZ"
    )
    private Instant startedAt;

    @Column(
            name = "ended_at",
            columnDefinition = "TIMESTAMPTZ"
    )
    private Instant endedAt;

    // =========================================================
    // DOMAIN METHODS — State Transitions
    //
    // These are the ONLY ways to mutate a Stream's state.
    // No setters. No direct field access.
    //
    // This pattern is called a "Rich Domain Model" — the entity
    // owns its own rules and enforces them internally.
    //
    // The alternative — "Anemic Domain Model" — puts this logic
    // in the service layer (or worse, scattered everywhere).
    // That leads to duplicate transition checks and inconsistent state.
    // =========================================================

    /**
     * Transitions the stream from CREATED → LIVE.
     *
     * Called by StreamService when the host starts screen sharing.
     * Validates the transition is legal before applying it.
     *
     * @throws IllegalStateException if the stream is not in CREATED state.
     *         The service layer catches this and returns a 409 Conflict.
     */
    public void start() {
        if (this.status != StreamStatus.CREATED) {
            throw new IllegalStateException(
                    String.format(
                            "Cannot start stream. Expected status CREATED but was %s.",
                            this.status
                    )
            );
        }
        this.status = StreamStatus.LIVE;
        this.startedAt = Instant.now();
    }

    /**
     * Transitions the stream from LIVE → ENDED.
     *
     * Called by StreamService when the host ends the stream.
     * Once ENDED, this stream object is effectively immutable.
     *
     * @throws IllegalStateException if the stream is not in LIVE state.
     *         The service layer catches this and returns a 409 Conflict.
     */
    public void end() {
        if (this.status != StreamStatus.LIVE) {
            throw new IllegalStateException(
                    String.format(
                            "Cannot end stream. Expected status LIVE but was %s.",
                            this.status
                    )
            );
        }
        this.status = StreamStatus.ENDED;
        this.endedAt = Instant.now();
    }

    /**
     * Updates the stream title.
     *
     * Only allowed while the stream is not ENDED.
     * Even if not in the MVP, this guard prevents editing a finished stream.
     *
     * @param newTitle the new title, must not be blank
     */
    public void updateTitle(String newTitle) {
        if (this.status == StreamStatus.ENDED) {
            throw new IllegalStateException(
                    "Cannot update title of an ended stream."
            );
        }
        if (newTitle == null || newTitle.isBlank()) {
            throw new IllegalArgumentException(
                    "Stream title must not be blank."
            );
        }
        this.title = newTitle;
    }

    /**
     * FACTORY METHOD — preferred way to create a new Stream.
     *
     * WHY A FACTORY METHOD AND NOT JUST THE BUILDER?
     * The builder lets anyone construct a Stream with any combination
     * of fields — including invalid combinations like a LIVE stream
     * with no startedAt, or an ENDED stream with no endedAt.
     *
     * This factory method enforces the correct initial state:
     *   - Status is always CREATED
     *   - createdAt is always set to now
     *   - startedAt and endedAt are always null initially
     *
     * Callers can't forget to set these or set them wrong.
     * The builder is still available for Hibernate and testing use.
     *
     * @param roomName  unique LiveKit room identifier
     * @param hostKey   secret host authentication token
     * @param title     human-readable stream title
     */

    /**
     * Updated factory method — now accepts category and streamType.
     * viewerCount and likesCount always start at 0.
     */
    public static Stream create(
            String roomName,
            String hostKey,
            String title,
            String category,
            StreamType streamType
    ) {
        return Stream.builder()
                .roomName(roomName)
                .hostKey(hostKey)
                .title(title)
                .category(category)
                .streamType(streamType)
                .status(StreamStatus.CREATED)
                .createdAt(Instant.now())
                .viewerCount(0)
                .likesCount(0)
                .build();
    }
}