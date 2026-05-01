package com.golivebackend.livekit.service;

import com.golivebackend.livekit.config.LiveKitProperties;
import com.golivebackend.livekit.dto.TokenRequest;
import com.golivebackend.livekit.dto.TokenResponse;
import com.golivebackend.livekit.model.ParticipantRole;
import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamStatus;
import com.golivebackend.stream.repository.StreamRepository;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.golivebackend.common.exception.StreamNotFoundException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitTokenService {

    private final StreamRepository streamRepository;
    private final LiveKitProperties liveKitProperties;

    @Transactional(readOnly = true)
    public TokenResponse generateToken(TokenRequest request) {
        log.info("Token request — streamId: {}, role: {}",
                request.streamId(), request.role());

        Stream stream = streamRepository
                .findByStreamId(request.streamId())
                .orElseThrow(() -> new StreamNotFoundException(
                        "Stream not found with id: " + request.streamId()
                ));

        validateStreamStateForJoining(stream);

        if (request.role() == ParticipantRole.HOST) {
            validateHostKey(request, stream);
        }

        return switch (request.role()) {
            case HOST   -> buildHostToken(stream);
            case VIEWER -> buildViewerToken(stream);
        };
    }

    // =========================================================
    // PRIVATE — Validation (unchanged)
    // =========================================================

    private void validateStreamStateForJoining(Stream stream) {
        if (stream.getStatus() == StreamStatus.ENDED) {
            throw new IllegalStateException(
                    "Cannot join an ended stream. Stream id: "
                            + stream.getStreamId()
            );
        }
    }

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
    // PRIVATE — Token Construction (FIXED)
    // =========================================================

    private TokenResponse buildHostToken(Stream stream) {
        String identity = "host-" + stream.getStreamId();

        AccessToken token = new AccessToken(
                liveKitProperties.getApiKey(),
                liveKitProperties.getApiSecret()
        );
        token.setName(identity);
        token.setIdentity(identity);
        token.setTtl(6 * 60 * 60);

        /*
         * Each permission is a concrete subclass of VideoGrant.
         * addGrants() accepts varargs — pass all grants in one call.
         *
         * RoomJoin(true)              → participant can enter the room
         * RoomName(roomName)          → restricts token to this room only
         * CanPublish(true)            → can send screen/camera/mic tracks
         * CanSubscribe(true)          → can receive tracks from others
         */
        token.addGrants(
                new RoomJoin(true),
                new RoomName(stream.getRoomName()),
                new CanPublish(true),
                new CanSubscribe(true)
        );

        log.info("HOST token generated for streamId: {}, room: {}",
                stream.getStreamId(), stream.getRoomName());

        return new TokenResponse(
                token.toJwt(),
                liveKitProperties.getUrl(),
                stream.getRoomName()
        );
    }

    private TokenResponse buildViewerToken(Stream stream) {
        String identity = "viewer-" + UUID.randomUUID();

        AccessToken token = new AccessToken(
                liveKitProperties.getApiKey(),
                liveKitProperties.getApiSecret()
        );
        token.setName(identity);
        token.setIdentity(identity);
        token.setTtl(6 * 60 * 60);

        /*
         * VIEWER permissions:
         * CanPublish(false)  → cannot send any media — strictly read only
         * CanSubscribe(true) → can receive the host's screen share
         *
         * CanPublish is explicitly false even though it may default
         * to false — in security-sensitive code we never rely on defaults.
         */
        token.addGrants(
                new RoomJoin(true),
                new RoomName(stream.getRoomName()),
                new CanPublish(false),
                new CanSubscribe(true)
        );

        log.info("VIEWER token generated for streamId: {}, room: {}",
                stream.getStreamId(), stream.getRoomName());

        return new TokenResponse(
                token.toJwt(),
                liveKitProperties.getUrl(),
                stream.getRoomName()
        );
    }
}