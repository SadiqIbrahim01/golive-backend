package com.golivebackend.stream.controller;

import com.golivebackend.stream.dto.StreamRequest;
import com.golivebackend.stream.dto.StreamResponse;
import com.golivebackend.stream.service.StreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/streams")
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;

    // =========================================================
    // STREAM CRUD
    // =========================================================

    /**
     * POST /api/streams
     * Creates a new stream. Returns 201 with host credentials.
     * hostKey is only ever returned here — never again after this.
     */
    @PostMapping
    public ResponseEntity<StreamResponse> createStream(
            @Valid @RequestBody StreamRequest request
    ) {
        log.info("POST /streams — title: {}, category: {}, type: {}",
                request.title(), request.category(), request.streamType());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(streamService.createStream(request));
    }

    /**
     * GET /api/streams/{id}
     * Returns public stream details. hostKey never included.
     */
    @GetMapping("/{id}")
    public ResponseEntity<StreamResponse> getStream(
            @PathVariable("id") UUID streamId
    ) {
        log.debug("GET /streams/{}", streamId);
        return ResponseEntity.ok(streamService.findById(streamId));
    }

    /**
     * GET /api/streams
     * GET /api/streams?query=gaming
     *
     * Without query: returns all LIVE streams ordered by start time.
     * With query:    returns LIVE streams where title or category
     *                matches the search term (case-insensitive).
     *
     * Always LIVE only — CREATED and ENDED streams are never returned.
     */
    @GetMapping
    public ResponseEntity<List<StreamResponse>> getStreams(
            @RequestParam(required = false) String query
    ) {
        if (query != null && !query.isBlank()) {
            log.debug("GET /streams?query={}", query);
            return ResponseEntity.ok(streamService.searchStreams(query));
        }
        log.debug("GET /streams — listing all LIVE streams");
        return ResponseEntity.ok(streamService.findLiveStreams());
    }

    // =========================================================
    // STREAM LIFECYCLE
    // =========================================================

    /**
     * PATCH /api/streams/{id}/start
     * Transitions stream CREATED → LIVE.
     * Requires X-Host-Key header matching the stream's hostKey.
     */
    @PatchMapping("/{id}/start")
    public ResponseEntity<StreamResponse> startStream(
            @PathVariable("id") UUID streamId,
            @RequestHeader(value = "X-Host-Key", required = false) String hostKey
    ) {
        log.info("PATCH /streams/{}/start", streamId);
        return ResponseEntity.ok(streamService.startStream(streamId, hostKey));
    }

    /**
     * PATCH /api/streams/{id}/end
     * Transitions stream LIVE → ENDED.
     * Requires X-Host-Key header matching the stream's hostKey.
     */
    @PatchMapping("/{id}/end")
    public ResponseEntity<StreamResponse> endStream(
            @PathVariable("id") UUID streamId,
            @RequestHeader(value = "X-Host-Key", required = false) String hostKey
    ) {
        log.info("PATCH /streams/{}/end", streamId);
        return ResponseEntity.ok(streamService.endStream(streamId, hostKey));
    }

    // =========================================================
    // LIKES
    // =========================================================

    /**
     * PATCH /api/streams/{id}/like
     * X-Session-Id: {frontend-generated UUID stored in localStorage}
     *
     * Idempotent — liking twice from the same session has no effect.
     * Returns current like count after the operation.
     */
    @PatchMapping("/{id}/like")
    public ResponseEntity<Map<String, Integer>> likeStream(
            @PathVariable("id") UUID streamId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        log.debug("PATCH /streams/{}/like — sessionId: {}", streamId, sessionId);

        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Like rejected — missing X-Session-Id header");
            return ResponseEntity.badRequest().build();
        }

        int likes = streamService.likeStream(streamId, sessionId);
        return ResponseEntity.ok(Map.of("likes", likes));
    }

    /**
     * PATCH /api/streams/{id}/unlike
     * X-Session-Id: {frontend-generated UUID stored in localStorage}
     *
     * Idempotent — unliking when not liked has no effect.
     * Returns current like count after the operation.
     */
    @PatchMapping("/{id}/unlike")
    public ResponseEntity<Map<String, Integer>> unlikeStream(
            @PathVariable("id") UUID streamId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        log.debug("PATCH /streams/{}/unlike — sessionId: {}", streamId, sessionId);

        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Unlike rejected — missing X-Session-Id header");
            return ResponseEntity.badRequest().build();
        }

        int likes = streamService.unlikeStream(streamId, sessionId);
        return ResponseEntity.ok(Map.of("likes", likes));
    }
}