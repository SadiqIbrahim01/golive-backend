package com.golivebackend.stream.controller;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import com.golivebackend.stream.dto.StreamRequest;
import com.golivebackend.stream.dto.StreamResponse;
import com.golivebackend.stream.service.StreamService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/streams")
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;

    // =========================================================
    // STREAM CRUD
    // =========================================================

    @PostMapping
    @RateLimiter(name = "stream-creation", fallbackMethod = "rateLimitedResponse")
    public ResponseEntity<StreamResponse> createStream(@Valid @RequestBody StreamRequest request) {
        log.info("POST /streams — title: {}, category: {}, type: {}",
                request.title(), request.category(), request.streamType());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(streamService.createStream(request));
    }

    /**
     * GET /streams/{id}
     * Cached for 3 seconds (high-frequency updates: likes/viewers)
     */
    @GetMapping("/{id}")
    public ResponseEntity<StreamResponse> getStream(
            @PathVariable("id") UUID streamId
    ) {
        log.debug("GET /streams/{}", streamId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(3, TimeUnit.SECONDS).cachePublic())
                .body(streamService.findById(streamId));
    }

    @GetMapping("/trending")
    public ResponseEntity<List<StreamResponse>> getTrendingStreams() {
        log.debug("GET /streams/trending — listing trending LIVE streams");

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.SECONDS).cachePublic())
                .body(streamService.findTrendingStreams());
    }

    /**
     * GET /streams
     * Cached for 5 seconds (stream listing endpoint)
     */
    @GetMapping
    public ResponseEntity<List<StreamResponse>> getStreams(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category
    ) {

        if (category != null && !category.isBlank()) {
            log.debug("GET /streams?category={}", category);

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.SECONDS).cachePublic())
                    .body(streamService.findLiveStreamsByCategory(category));
        }

        if (query != null && !query.isBlank()) {

            if (query.trim().length() < 2) {
                log.warn("Rejected search query — too short: {}", query);
                return ResponseEntity.badRequest().build();
            }

            log.debug("GET /streams?query={}", query);

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.SECONDS).cachePublic())
                    .body(streamService.searchStreams(query));
        }

        log.debug("GET /streams — listing all LIVE streams");

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.SECONDS).cachePublic())
                .body(streamService.findLiveStreams());
    }

    // =========================================================
    // STREAM LIFECYCLE
    // =========================================================

    @PatchMapping("/{id}/start")
    public ResponseEntity<StreamResponse> startStream(
            @PathVariable("id") UUID streamId,
            @RequestHeader(value = "X-Host-Key", required = false) String hostKey
    ) {
        log.info("PATCH /streams/{}/start", streamId);

        return ResponseEntity.ok(
                streamService.startStream(streamId, hostKey)
        );
    }

    @PatchMapping("/{id}/end")
    public ResponseEntity<StreamResponse> endStream(
            @PathVariable("id") UUID streamId,
            @RequestHeader(value = "X-Host-Key", required = false) String hostKey
    ) {
        log.info("PATCH /streams/{}/end", streamId);

        return ResponseEntity.ok(
                streamService.endStream(streamId, hostKey)
        );
    }

    // =========================================================
    // LIKES (UNCHANGED)
    // =========================================================

    /**
     * PATCH /api/streams/{id}/like
     * No authentication required. No session tracking.
     * Each call increments the count by 1.
     */
    @PatchMapping("/{id}/like")
    @RateLimiter(name = "stream-likes", fallbackMethod = "rateLimitedLikeResponse")
    public ResponseEntity<Map<String, Integer>> likeStream(
            @PathVariable("id") UUID streamId
    ) {
        log.debug("PATCH /streams/{}/like", streamId);
        return ResponseEntity.ok(Map.of("likes", streamService.likeStream(streamId)));
    }

    /**
     * PATCH /api/streams/{id}/unlike
     * No authentication required. No session tracking.
     * Each call decrements the count by 1 (floor at 0).
     */
    @PatchMapping("/{id}/unlike")
    @RateLimiter(name = "stream-likes", fallbackMethod = "rateLimitedLikeResponse")
    public ResponseEntity<Map<String, Integer>> unlikeStream(
            @PathVariable("id") UUID streamId
    ) {
        log.debug("PATCH /streams/{}/unlike", streamId);
        return ResponseEntity.ok(Map.of("likes", streamService.unlikeStream(streamId)));
    }

    // =========================================================
    // RATE LIMIT FALLBACKS
    // =========================================================

    /**
     * Rate limiter fallback for like/unlike.
     * Signature must match the method it covers exactly.
     */
    public ResponseEntity<Map<String, Integer>> rateLimitedLikeResponse(UUID streamId, Throwable t) {
        log.warn("Rate limit exceeded for like/unlike — streamId: {}", streamId);
        Map<String, Integer> errorBody = Map.of("errorCode", 429);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(errorBody);
    }

    public ResponseEntity<Map<String, Integer>> rateLimitedIntResponse(
            UUID streamId,
            String sessionId,
            Throwable t
    ) {
        log.warn("Like rate limit exceeded — streamId: {}, sessionId: {}",
                streamId, sessionId);

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .build();
    }
    public ResponseEntity<StreamResponse> rateLimitedResponse(StreamRequest request, RequestNotPermitted ex) {
        log.warn("Rate limit exceeded for stream creation! Title: {}, Category: {}",
                request.title(), request.category());

        // Option A: Return 429 with an empty body (Requires StreamResponse to allow null or empty checks)
//        return ResponseEntity
//                .status(HttpStatus.TOO_MANY_REQUESTS)
//                .build();


    StreamResponse errorResponse = new StreamResponse(
        null, // or custom error ID
        "Rate limit exceeded. Please try again later.",
    null,null,null,null,null,null,0,0,null,
            null,null);
    return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(errorResponse);

    }

}