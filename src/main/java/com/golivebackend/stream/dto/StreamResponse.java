package com.golivebackend.stream.dto;

import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound DTO for stream responses.
 *
 * CRITICAL DESIGN DECISION — TWO FACTORY METHODS:
 *
 * toPublicResponse(stream)  → for viewers and listings
 *   Does NOT include hostKey or hostUrl
 *   Safe to return to anyone
 *
 * toHostResponse(stream)    → for stream creation only
 *   DOES include hostKey and hostUrl
 *   Returned exactly ONCE — on POST /api/streams
 *   Never returned again after that
 *
 * This separation is the key security control for this MVP.
 * If you accidentally use toPublicResponse on the creation endpoint,
 * the host has no way to manage their stream.
 * If you accidentally use toHostResponse on the watch endpoint,
 * hostKey is leaked to every viewer.
 *
 * Having two named methods makes the intent explicit and the
 * mistake obvious in code review.
 */
public record StreamResponse(

        String streamId,
        String title,
        StreamStatus status,
        String watchUrl,

        /*
         * hostUrl and hostKey are null in public responses.
         * Jackson's 'non_null' setting (in application.yml) means
         * null fields are omitted from the JSON output automatically.
         * Viewers never see "host_url": null — the field simply
         * doesn't appear.
         */
        String hostUrl,
        String hostKey,

        Instant createdAt,
        Instant startedAt,
        Instant endedAt

) {

    /**
     * Public response — safe for viewers and listings.
     * hostKey and hostUrl are NOT included.
     */
    public static StreamResponse toPublicResponse(Stream stream) {
        return new StreamResponse(
                stream.getStreamId().toString(),
                stream.getTitle(),
                stream.getStatus(),
                buildWatchUrl(stream.getStreamId()),
                null,   // hostUrl — not for public
                null,   // hostKey — never exposed publicly
                stream.getCreatedAt(),
                stream.getStartedAt(),
                stream.getEndedAt()
        );
    }

    /**
     * Host response — returned ONCE on stream creation.
     * Includes hostKey and hostUrl.
     *
     * The frontend must store these immediately —
     * they will not be retrievable again from this API.
     */
    public static StreamResponse toHostResponse(Stream stream) {
        return new StreamResponse(
                stream.getStreamId().toString(),
                stream.getTitle(),
                stream.getStatus(),
                buildWatchUrl(stream.getStreamId()),
                buildHostUrl( stream.getStreamId(), stream.getHostKey()),
                stream.getHostKey(),
                stream.getCreatedAt(),
                stream.getStartedAt(),
                stream.getEndedAt()
        );
    }

    /*
     * URL BUILDERS
     *
     * Hardcoded to localhost for now.
     * On Day 6 (deployment) we'll inject FRONTEND_URL from
     * config and build these dynamically.
     *
     * We centralise URL construction here so when the base URL
     * changes, it changes in one place.
     */
    private static String buildWatchUrl(UUID streamId) {
        return "http://localhost:5173/watch/" + streamId;
    }

    private static String buildHostUrl(UUID streamId, String hostKey) {
        return "http://localhost:5173/stream-setup/" + streamId + "?hostKey=" + hostKey;
    }
}