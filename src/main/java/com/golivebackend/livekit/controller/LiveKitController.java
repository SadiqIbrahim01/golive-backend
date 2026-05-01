package com.golivebackend.livekit.controller;

import com.golivebackend.livekit.dto.TokenRequest;
import com.golivebackend.livekit.dto.TokenResponse;
import com.golivebackend.livekit.service.LiveKitTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point for LiveKit token generation.
 *
 * Single endpoint. Single responsibility.
 * The role field in the request body determines token type.
 *
 * WHY ONE ENDPOINT RATHER THAN /token/host AND /token/viewer?
 * One endpoint with a role parameter is simpler and more extensible.
 * Adding a MODERATOR role later means no new endpoint — just a new
 * enum value and a new branch in the service. Clean.
 */
@Slf4j
@RestController
@RequestMapping("/livekit")
@RequiredArgsConstructor
public class LiveKitController {

    private final LiveKitTokenService liveKitTokenService;

    /**
     * POST /api/livekit/token
     *
     * Request (HOST):
     * {
     *   "stream_id": "550e8400-...",
     *   "role": "HOST",
     *   "host_key": "your-secret-host-key"
     * }
     *
     * Request (VIEWER):
     * {
     *   "stream_id": "550e8400-...",
     *   "role": "VIEWER"
     * }
     *
     * Response:
     * {
     *   "token": "eyJhbGci...",
     *   "livekit_url": "wss://your-project.livekit.cloud",
     *   "room_name": "room-abc123"
     * }
     *
     * HTTP 200 (not 201) because we're not creating a persistent resource.
     * A JWT token is ephemeral — it exists in memory, not in the DB.
     */
    @PostMapping("/token")
    public ResponseEntity<TokenResponse> generateToken(
            @Valid @RequestBody TokenRequest request
    ) {
        log.info("POST /livekit/token — streamId: {}, role: {}",
                request.streamId(), request.role());

        return ResponseEntity.ok(liveKitTokenService.generateToken(request));
    }
}