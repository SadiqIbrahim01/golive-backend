package com.golivebackend.livekit.controller;

import com.golivebackend.livekit.dto.TokenRequest;
import com.golivebackend.livekit.dto.TokenResponse;
import com.golivebackend.livekit.service.LiveKitTokenService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP entry point for LiveKit token generation.
 *
 * Single endpoint. Single responsibility.
 * The role field in the request body determines token type.
 *
 * WHY ONE ENDPOINT RATHER THAN /token/host AND /token/viewer?
 * One endpoint with a role parameter is simpler and more extensible.
 * Adding a MODERATOR role later means no new endpoint — just a new
 * enum value and service branch.
 */
@Slf4j
@RestController
@RequestMapping("/livekit")
@RequiredArgsConstructor
public class LiveKitController {

    private final LiveKitTokenService liveKitTokenService;

    /**
     * POST /livekit/token
     *
     * HOST request:
     * {
     *   "stream_id":"...",
     *   "role":"HOST",
     *   "host_key":"..."
     * }
     *
     * VIEWER request:
     * {
     *   "stream_id":"...",
     *   "role":"VIEWER"
     * }
     *
     * Rate limiting exists to prevent:
     * - token generation abuse
     * - bot floods
     * - stream join spam
     * - JWT generation pressure
     */
    @PostMapping("/token")
    @RateLimiter(
            name = "token-generation",
            fallbackMethod = "rateLimitedResponse"
    )
    public ResponseEntity<TokenResponse> generateToken(
            @Valid @RequestBody TokenRequest request
    ) {

        log.info(
                "POST /livekit/token — streamId: {}, role: {}",
                request.streamId(),
                request.role()
        );

        TokenResponse response =
                liveKitTokenService.generateToken(request);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // RATE LIMIT FALLBACKS
    // =========================================================

    /**
     * Invoked when token-generation limit is exceeded.
     */
    public ResponseEntity<TokenResponse> rateLimitedResponse(TokenRequest request, Throwable t
    ) {

        log.warn(
                "Token generation rate limit exceeded — streamId: {}, role: {}, cause: {}",
                request != null ? request.streamId() : null,
                request != null ? request.role() : null,
                t.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .build();
    }
}