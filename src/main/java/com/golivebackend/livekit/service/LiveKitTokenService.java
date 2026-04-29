package com.golivebackend.livekit.service;

import com.golivebackend.livekit.config.LiveKitProperties;
import com.golivebackend.livekit.dto.TokenRequest;
import com.golivebackend.livekit.dto.TokenResponse;
import com.golivebackend.livekit.model.ParticipantRole;
import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamStatus;
import com.golivebackend.stream.repository.StreamRepository;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.WebhookReceiver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Generates signed LiveKit JWT tokens for hosts and viewers.
 *
 * SECURITY RESPONSIBILITIES OF THIS CLASS:
 *   1. Verify the stream exists before issuing any token
 *   2. Verify host identity (hostKey) before issuing publish permissions
 *   3. Verify the stream is in a valid state for joining
 *   4. Issue tokens with the minimum permissions required per role
 *
 * CROSS-MODULE NOTE:
 * This service directly injects StreamRepository from the stream module.
 * In a stricter modular monolith, it would call StreamService instead,
 * keeping the livekit module ignorant of stream persistence details.
 * For this MVP, direct repository access is pragmatic and acceptable.
 * The upgrade path: extract a StreamQueryService with read-only methods
 * that livekit and other modules can call safely.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitTokenService {

    private final StreamRepository streamRepository;
    private final LiveKitProperties liveKitProperties;

    /**
     * Generates a LiveKit access token for the given stream and role.
     *
     * FLOW:
     *   1. Load the stream (404 if not found)
     *   2. Validate stream state (must be CREATED or LIVE to join)
     *   3. If HOST: verify hostKey (403 if invalid)
     *   4. Build token with appropriate permissions
     *   5. Return signed token + LiveKit URL + roomName
     *
     * @Transactional(readOnly = true): we only read the stream,
     * no modifications needed here.
     */
    @Transactional(readOnly = true)
    public TokenResponse generateToken(TokenRequest request) {
        log.info("Token request — streamId: {}, role: {}",
                request.streamId(), request.role());

        // Step 1: Load stream
        Stream stream = streamRepository
                .findByStreamId(request.streamId())
                .orElseThrow(() -> new StreamNotFoundException(
                        "Stream not found with id: " + request.streamId()
                ));

        // Step 2: Validate stream state
        validateStreamStateForJoining(stream);

        // Step 3: Host verification
        if (request.role() == ParticipantRole.HOST) {
            validateHostKey(request, stream);
        }

        // Step 4 + 5: Build and return token
        return switch (request.role()) {
            case HOST   -> buildHostToken(stream);
            case VIEWER -> buildViewerToken(stream);
        };

        /*
         * WHY switch EXPRESSION AND NOT if/else?
         * Java 21 switch expressions are exhaustive — the compiler
         * forces you to handle every enum value. If you add a new
         * ParticipantRole (e.g. MODERATOR) and forget to handle it
         * here, the code won't compile. if/else would silently fall
         * through. Exhaustive switches are a safety net.
         */
    }

    // =========================================================
    // PRIVATE — Validation
    // =========================================================

    /**
     * A token should only be issued for streams that are
     * joinable — CREATED (host is setting up) or LIVE (stream running).
     *
     * ENDED streams are immutable. Issuing a token for an ended stream
     * would let someone join a dead LiveKit room — confusing and wasteful.
     */
    private void validateStreamStateForJoining(Stream stream) {
        if (stream.getStatus() == StreamStatus.ENDED) {
            throw new IllegalStateException(
                    "Cannot join an ended stream. Stream id: "
                            + stream.getStreamId()
            );
        }
    }

    /**
     * Validates that the provided hostKey matches the stream's hostKey.
     *
     * WHY NOT USE .equals() DIRECTLY ON THE STREAM?
     * We centralise this check here so:
     *   1. The "hostKey is missing for HOST role" check is in one place
     *   2. The "hostKey doesn't match" check is in one place
     *   3. Both throw the same exception type → mapped to 403 by handler
     *
     * Note: we throw IllegalArgumentException (→ 403 Forbidden) rather
     * than returning a specific "invalid key" message. This is intentional
     * — we don't want to tell a potential attacker whether the stream
     * exists but the key is wrong, or the stream doesn't exist at all.
     */
    private void validateHostKey(TokenRequest request, Stream stream) {
        if (request.hostKey() == null || request.hostKey().isBlank()) {
            throw new UnauthorisedHostException(
                    "hostKey is required for HOST role"
            );
        }

        if (!stream.getHostKey().equals(request.hostKey())) {
            throw new UnauthorisedHostException(
                    "Invalid hostKey for stream: " + stream.getStreamId()
            );
        }
    }

    // =========================================================
    // PRIVATE — Token Construction
    // =========================================================

    /**
     * Builds a host token with publish + subscribe permissions.
     *
     * PARTICIPANT IDENTITY:
     * "host-{streamId}" gives the host a stable, identifiable
     * presence in the LiveKit room. Useful for:
     *   - LiveKit dashboard visibility
     *   - Future moderation features
     *   - Distinguishing host from viewers in room events
     */
    private TokenResponse buildHostToken(Stream stream) {
        String identity = "host-" + stream.getStreamId();

        AccessToken token = new AccessToken(
                liveKitProperties.getApiKey(),
                liveKitProperties.getApiSecret()
        );

        token.setName(identity);
        token.setIdentity(identity);

        /*
         * RoomJoin grant — the core permission:
         *   roomJoin:     can enter the room
         *   canPublish:   can send video/audio/screen tracks
         *   canSubscribe: can receive tracks from others
         *
         * @RoomName: the specific LiveKit room this token is valid for.
         * A token with room "room-abc" cannot join "room-xyz".
         * This is enforced by LiveKit's server — not just convention.
         */
        token.addGrants(new RoomJoin(true), new RoomName(stream.getRoomName()));
        token.getVideoGrant().setCanPublish(true);
        token.getVideoGrant().setCanSubscribe(true);

        /*
         * Token expiry: 6 hours.
         * A reasonable session length for a live stream.
         * If the stream runs longer, the host would need to
         * re-request a token — acceptable for MVP.
         *
         * In seconds: 6 * 60 * 60 = 21600
         */
        token.setTtl(java.time.Duration.ofHours(6));

        log.info("HOST token generated for streamId: {}, room: {}",
                stream.getStreamId(), stream.getRoomName());

        return new TokenResponse(
                token.toJwt(),
                liveKitProperties.getUrl(),
                stream.getRoomName()
        );
    }

    /**
     * Builds a viewer token with subscribe-only permissions.
     *
     * IDENTITY FORMAT: "viewer-{random UUID}"
     * Each viewer gets a unique identity so LiveKit can track
     * individual participants in the room.
     *
     * WHY RANDOM AND NOT USER-BASED?
     * No accounts exist in this MVP. Random UUID per viewer is
     * the correct approach — it's unique per session, not per person.
     */
    private TokenResponse buildViewerToken(Stream stream) {
        String identity = "viewer-" + UUID.randomUUID();

        AccessToken token = new AccessToken(
                liveKitProperties.getApiKey(),
                liveKitProperties.getApiSecret()
        );

        token.setName(identity);
        token.setIdentity(identity);

        token.addGrants(new RoomJoin(true), new RoomName(stream.getRoomName()));

        /*
         * VIEWER PERMISSIONS:
         *   canPublish   = false → cannot send any media tracks
         *   canSubscribe = true  → can receive the host's screen share
         *
         * canPublish defaults to false in the SDK but we set it
         * explicitly — in security-sensitive code, explicit is always
         * better than relying on defaults that may change between SDK versions.
         */
        token.getVideoGrant().setCanPublish(false);
        token.getVideoGrant().setCanSubscribe(true);

        token.setTtl(java.time.Duration.ofHours(6));

        log.info("VIEWER token generated for streamId: {}, room: {}",
                stream.getStreamId(), stream.getRoomName());

        return new TokenResponse(
                token.toJwt(),
                liveKitProperties.getUrl(),
                stream.getRoomName()
        );
    }
}