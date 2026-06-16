package com.golivebackend.stream.service;

import com.golivebackend.common.exception.StreamNotFoundException;
import com.golivebackend.livekit.service.UnauthorisedHostException;
import com.golivebackend.stream.dto.StreamRequest;
import com.golivebackend.stream.dto.StreamResponse;
import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamStatus;
import com.golivebackend.stream.model.StreamType;
import com.golivebackend.stream.repository.StreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final StreamRepository streamRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // =========================================================
    // STREAM CRUD
    // =========================================================

    /**
     * Creates a new stream session.
     * Returns a host response — includes hostKey and hostUrl.
     * This is the ONLY time hostKey is ever returned.
     */
    @Transactional
    public StreamResponse createStream(StreamRequest request) {
        log.info("Creating new stream with title: {}", request.title());

        String roomName = generateRoomName();
        String hostKey  = generateHostKey();

        /*
         * Default streamType to SCREEN_SHARE if not provided.
         * Keeps the API backwards compatible with frontends
         * that don't yet send this field.
         */
        StreamType streamType = request.streamType() != null
                ? request.streamType()
                : StreamType.SCREEN_SHARE;

        String normalisedCategory = request.category() != null
                ? request.category().trim().toLowerCase()
                : null;

        Stream stream = Stream.create(
                roomName,
                hostKey,
                request.title(),
                normalisedCategory,
                streamType
        );

        Stream saved = streamRepository.save(stream);

        log.info("Stream created — streamId: {}, type: {}, category: {}",
                saved.getStreamId(), saved.getStreamType(), saved.getCategory());

        return StreamResponse.toHostResponse(saved);
    }

    /**
     * Retrieves a single stream by its public ID.
     * Returns a public response — hostKey never included.
     *
     * @throws StreamNotFoundException if no stream exists with this ID
     */
    @Transactional(readOnly = true)
    public StreamResponse findById(UUID streamId) {
        log.debug("Fetching stream by id: {}", streamId);

        Stream stream = streamRepository.findByStreamId(streamId)
                .orElseThrow(() -> new StreamNotFoundException(
                        "Stream not found with id: " + streamId
                ));

        return StreamResponse.toPublicResponse(stream);
    }

    /**
     * Returns all currently LIVE streams ordered by start time descending.
     * Used by GET /api/streams when no search query is provided.
     */
    @Transactional(readOnly = true)
    public List<StreamResponse> findLiveStreams() {
        log.debug("Fetching all LIVE streams");

        return streamRepository
                .findLiveStreamsOrderedByStartTime(StreamStatus.LIVE)
                .stream()
                .map(StreamResponse::toPublicResponse)
                .toList();
    }

    /**
     * Searches LIVE streams by title or category.
     * Case-insensitive. Returns only LIVE streams.
     * Used by GET /api/streams?query={value}
     *
     * @param query the search term — wrapped in % wildcards here
     */
    @Transactional(readOnly = true)
    public List<StreamResponse> searchStreams(String query) {
        log.debug("Searching LIVE streams with query: {}", query);

        /*
         * % wildcards applied here in the service — not in the repository.
         * The repository stays clean (just a query, no string manipulation).
         * The service owns the "how we search" decision.
         */
        String likePattern = "%" + query.trim() + "%";

        return streamRepository
                .searchLiveStreams(likePattern)
                .stream()
                .map(StreamResponse::toPublicResponse)
                .toList();
    }

    /**
     * Finds LIVE streams matching a specific category.
     */
    @Transactional(readOnly = true)
    public List<StreamResponse> findLiveStreamsByCategory(String category) {
        log.debug("Fetching LIVE streams for category: {}", category);
        return streamRepository
                .findLiveStreamsByCategory(category.trim())
                .stream()
                .map(StreamResponse::toPublicResponse)
                .toList();
    }

    /**
     * Finds LIVE streams ordered by viewer count descending.
     */
    @Transactional(readOnly = true)
    public List<StreamResponse> findTrendingStreams() {
        log.debug("Fetching trending LIVE streams");
        return streamRepository
                .findTrendingStreams()
                .stream()
                .map(StreamResponse::toPublicResponse)
                .toList();
    }

    // =========================================================
    // STREAM LIFECYCLE
    // =========================================================

    /**
     * Transitions stream from CREATED → LIVE.
     * Requires a valid hostKey in the X-Host-Key header.
     *
     * @throws StreamNotFoundException   if stream not found  (→ 404)
     * @throws UnauthorisedHostException if hostKey is wrong  (→ 403)
     * @throws IllegalStateException     if not in CREATED state (→ 409)
     */
    @Transactional
    public StreamResponse startStream(UUID streamId, String hostKey) {
        log.info("Start request — streamId: {}", streamId);

        Stream stream = loadAndAuthoriseStream(streamId, hostKey);
        stream.start();

        Stream saved = streamRepository.save(stream);

        log.info("Stream started — streamId: {}, status: {}",
                saved.getStreamId(), saved.getStatus());

        return StreamResponse.toPublicResponse(saved);
    }

    /**
     * Transitions stream from LIVE → ENDED.
     * Requires a valid hostKey in the X-Host-Key header.
     *
     * @throws StreamNotFoundException   if stream not found (→ 404)
     * @throws UnauthorisedHostException if hostKey is wrong (→ 403)
     * @throws IllegalStateException     if not in LIVE state (→ 409)
     */
    @Transactional
    public StreamResponse endStream(UUID streamId, String hostKey) {
        log.info("End request — streamId: {}", streamId);

        Stream stream = loadAndAuthoriseStream(streamId, hostKey);
        stream.end();

        Stream saved = streamRepository.save(stream);

        /*
         * Notify all connected viewers that the stream has ended.
         * The frontend should listen for messages of type STREAM_ENDED
         * on the chat topic and redirect the viewer appropriately.
         *
         * We broadcast to the same topic viewers are already subscribed to
         * — no new subscription required on the frontend.
         *
         * WHY AFTER save() AND NOT BEFORE?
         * The DB must reflect ENDED status before we tell clients.
         * If we broadcast first and the save fails, clients think the
         * stream ended but our DB still shows LIVE — inconsistent state.
         */
        messagingTemplate.convertAndSend(
                "/topic/streams/" + streamId + "/chat",
                java.util.Map.of(
                        "type", "STREAM_ENDED",
                        "stream_id", streamId.toString(),
                        "timestamp", java.time.Instant.now().toString()
                )
        );

        log.info("Stream ended — streamId: {}, broadcast sent to subscribers",
                saved.getStreamId());

        return StreamResponse.toPublicResponse(saved);
    }

    // =========================================================
    // LIKES
    // =========================================================

    /**
     * Increments the like count for a stream.
     * No deduplication — a viewer can like as many times as they choose.
     * Single atomic DB operation. No race condition possible.
     */
    @Transactional
    public int likeStream(UUID streamId) {
        Stream stream = streamRepository.findByStreamId(streamId)
                .orElseThrow(() -> new StreamNotFoundException(
                        "Stream not found: " + streamId));

        streamRepository.incrementLikes(streamId);

        log.info("Stream {} liked — new count approx: {}",
                streamId, stream.getLikesCount() + 1);

        return stream.getLikesCount() + 1;
    }

    /**
     * Decrements the like count for a stream.
     * Floor at 0 enforced by GREATEST() in the native query.
     * Idempotent only in the sense that it cannot go below zero.
     */
    @Transactional
    public int unlikeStream(UUID streamId) {
        Stream stream = streamRepository.findByStreamId(streamId)
                .orElseThrow(() -> new StreamNotFoundException(
                        "Stream not found: " + streamId));

        streamRepository.decrementLikes(streamId);

        log.info("Stream {} unliked — new count approx: {}",
                streamId, Math.max(stream.getLikesCount() - 1, 0));

        return Math.max(stream.getLikesCount() - 1, 0);
    }

    // =========================================================
    // VIEWER COUNT
    // =========================================================

    /**
     * Atomically increments the viewer count for a stream.
     * Called by WebSocketEventListener when a session subscribes
     * to a stream's chat topic.
     */
    @Transactional
    public void incrementViewerCount(UUID streamId) {
        log.debug("Incrementing viewer count for streamId: {}", streamId);
        streamRepository.incrementViewerCount(streamId);
        int currentCount = streamRepository.getViewerCount(streamId);
        broadcastViewerCount(streamId, currentCount);
    }

    /**
     * Atomically decrements the viewer count for a stream.
     * Floor is 0 — enforced by GREATEST() in the repository query.
     * Called by WebSocketEventListener when a session disconnects.
     */
    @Transactional
    public void decrementViewerCount(UUID streamId) {
        log.debug("Decrementing viewer count for streamId: {}", streamId);
        streamRepository.decrementViewerCount(streamId);
        int currentCount = streamRepository.getViewerCount(streamId);
        broadcastViewerCount(streamId, currentCount);
    }

    private void broadcastViewerCount(UUID streamId, int viewerCount) {
        messagingTemplate.convertAndSend(
                "/topic/streams/" + streamId + "/chat",
                java.util.Map.of(
                        "type", "VIEWER_COUNT",
                        "stream_id", streamId.toString(),
                        "viewer_count", viewerCount
                )
        );
        log.info("Broadcasted viewer count update for stream {} to {}", streamId, viewerCount);
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    /**
     * Loads a stream by ID and validates the hostKey.
     * Shared by startStream() and endStream() to ensure
     * auth logic never drifts between the two methods.
     *
     * @throws StreamNotFoundException   if stream not found
     * @throws UnauthorisedHostException if hostKey is missing or wrong
     */
    private Stream loadAndAuthoriseStream(UUID streamId, String hostKey) {
        Stream stream = streamRepository.findByStreamId(streamId)
                .orElseThrow(() -> new StreamNotFoundException(
                        "Stream not found with id: " + streamId
                ));

        if (hostKey == null || hostKey.isBlank()) {
            throw new UnauthorisedHostException(
                    "X-Host-Key header is required"
            );
        }

        if (!stream.getHostKey().equals(hostKey)) {
            throw new UnauthorisedHostException(
                    "Invalid host key for stream: " + streamId
            );
        }

        return stream;
    }

    private String generateRoomName() {
        return "room-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateHostKey() {
        return UUID.randomUUID().toString();
    }
}