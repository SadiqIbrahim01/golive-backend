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
import java.util.UUID;

/**
 * HTTP entry point for stream operations.
 *
 * RESPONSIBILITIES:
 *   ✅ Parse HTTP input (path variables, query params, request body)
 *   ✅ Call the service layer
 *   ✅ Return the correct HTTP status code
 *   ❌ No business logic — that's StreamService
 *   ❌ No database access — that's StreamRepository
 *
 * If a controller method is more than ~10 lines,
 * business logic has leaked into the wrong layer.
 */
@Slf4j
@RestController
@RequestMapping("/streams")
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;

    /**
     * POST /api/streams
     * Creates a new stream. Returns 201 Created with host credentials.
     *
     * @Valid triggers Bean Validation on StreamRequest.
     * If title is blank or too short, Spring returns 400 Bad Request
     * before this method body even runs.
     *
     * ResponseEntity.status(CREATED).body(...):
     * HTTP 201 is more precise than 200 for resource creation.
     * It signals "a new resource was created" — clients and
     * API gateways can use this distinction.
     */
    @PostMapping
    public ResponseEntity<StreamResponse> createStream(
            @Valid @RequestBody StreamRequest request
    ) {
        log.info("POST /streams — creating stream with title: {}", request.title());
        StreamResponse response = streamService.createStream(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * GET /api/streams/{id}
     * Returns public stream details by ID.
     *
     * {id} in the path → @PathVariable UUID streamId
     * Spring automatically converts the String path segment to UUID.
     * If the value isn't a valid UUID format, Spring returns 400
     * before we touch the service.
     */
    @GetMapping("/{id}")
    public ResponseEntity<StreamResponse> getStream(
            @PathVariable("id") UUID streamId
    ) {
        log.debug("GET /streams/{}", streamId);
        return ResponseEntity.ok(streamService.findById(streamId));
    }

    /**
     * GET /api/streams?status=LIVE
     * Lists streams filtered by status.
     *
     * @RequestParam(required = false): status is optional.
     * If not provided (GET /api/streams), returns all live streams
     * by default — the most useful default for the home page.
     *
     * WHY NOT A SEPARATE /live ENDPOINT?
     * A query parameter keeps the resource URL clean (/streams)
     * and follows REST conventions for filtering collections.
     * It also allows future filters: ?status=ENDED, ?status=CREATED.
     */
    @GetMapping
    public ResponseEntity<List<StreamResponse>> getStreams(
            @RequestParam(required = false, defaultValue = "LIVE") String status
    ) {
        log.debug("GET /streams?status={}", status);
        return ResponseEntity.ok(streamService.findLiveStreams());
    }

    /**
     * PATCH /api/streams/{id}/start
     *
     * Transitions stream from CREATED → LIVE.
     *
     * WHY PATCH AND NOT POST OR PUT?
     * - POST   → creates a new resource
     * - PUT    → replaces an entire resource
     * - PATCH  → applies a partial update to a resource
     *
     * We're changing one field (status) on an existing resource.
     * PATCH is semantically correct.
     *
     * @RequestHeader("X-Host-Key"): Spring extracts the value of the
     * X-Host-Key header and passes it as a String parameter.
     * If the header is missing entirely, Spring returns 400 Bad Request
     * before our method runs.
     *
     * required = false: we handle the missing case ourselves in the
     * service so we can return 403 (not 400) — more semantically
     * correct for an auth failure than a bad request error.
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
     *
     * Transitions stream from LIVE → ENDED.
     * Same hostKey validation pattern as /start.
     */
    @PatchMapping("/{id}/end")
    public ResponseEntity<StreamResponse> endStream(
            @PathVariable("id") UUID streamId,
            @RequestHeader(value = "X-Host-Key", required = false) String hostKey
    ) {
        log.info("PATCH /streams/{}/end", streamId);
        return ResponseEntity.ok(streamService.endStream(streamId, hostKey));
    }
}