package com.golivebackend.admin;

import com.golivebackend.common.exception.StreamNotFoundException;
import com.golivebackend.stream.dto.StreamResponse;
import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamStatus;
import com.golivebackend.stream.repository.StreamRepository;
import com.golivebackend.stream.service.StreamCacheService;
import com.golivebackend.stream.service.ViewerCountCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service layer for admin-only stream operations.
 *
 * SEPARATION OF CONCERNS:
 * Admin operations are intentionally separated from StreamService.
 * Mixing them would:
 *   1. Violate Single Responsibility Principle — StreamService handles host flows
 *   2. Force StreamService to know about admin bypass logic
 *   3. Create confusion about which methods require host auth vs admin auth
 *
 * AdminStreamService depends on StreamRepository directly (not on StreamService)
 * to avoid any coupling between the two service layers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStreamService {

    private final StreamRepository streamRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ViewerCountCacheService viewerCountCacheService;
    private final StreamCacheService streamCacheService;

    /**
     * Returns ALL streams regardless of status.
     * Ordered by createdAt descending (most recent first).
     *
     * @return list of all stream responses (public projection — no hostKey)
     */
    @Transactional(readOnly = true)
    public List<StreamResponse> getAllStreams() {
        log.debug("Admin: fetching all streams");
        return streamRepository.findAll()
                .stream()
                .map(StreamResponse::toPublicResponse)
                .toList();
    }

    /**
     * Returns aggregate stats across all streams.
     *
     * endedToday uses UTC midnight as the cutoff — consistent regardless
     * of the server's local timezone setting.
     *
     * @return stats record with totalStreams, liveNow, endedToday, totalViewers
     */
    @Transactional(readOnly = true)
    public AdminStreamStatsResponse getStats() {
        log.debug("Admin: computing stream stats");

        List<Stream> all = streamRepository.findAll();

        Instant startOfToday = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant();

        long totalStreams = all.size();
        long liveNow = all.stream()
                .filter(s -> s.getStatus() == StreamStatus.LIVE)
                .count();
        long endedToday = all.stream()
                .filter(s -> s.getStatus() == StreamStatus.ENDED
                        && s.getEndedAt() != null
                        && s.getEndedAt().isAfter(startOfToday))
                .count();
        long totalViewers = all.stream()
                .filter(s -> s.getStatus() == StreamStatus.LIVE)
                .mapToLong(Stream::getViewerCount)
                .sum();

        return new AdminStreamStatsResponse(totalStreams, liveNow, endedToday, totalViewers);
    }

    /**
     * Force-ends any LIVE stream without requiring the host key.
     *
     * Admin authority replaces the host key check — the JWT is the authority here.
     *
     * After transitioning to ENDED:
     *   1. Persists the updated status.
     *   2. Broadcasts STREAM_ENDED over WebSocket so viewers are notified
     *      (same event the host sends when ending normally).
     *
     * @param streamId the UUID of the stream to force-end
     * @return public StreamResponse of the now-ENDED stream
     * @throws StreamNotFoundException  if no stream with this ID exists
     * @throws IllegalStateException    if the stream is not LIVE (enforced by domain method)
     */
    @Transactional
    public StreamResponse forceEndStream(UUID streamId) {
        log.info("Admin force-end requested for streamId: {}", streamId);

        Stream stream = streamCacheService.findByStreamId(streamId)
                .orElseThrow(() -> new StreamNotFoundException(
                        "Stream not found with id: " + streamId
                ));

        /*
         * Delegate to the domain method — it enforces the LIVE → ENDED
         * transition rule and sets endedAt. Admin bypass means we skip
         * the host key check, but the domain invariant still applies.
         */
        stream.end();

        // Sync final count from Redis cache to DB and clear Redis key
        viewerCountCacheService.syncViewerCountToDatabase(streamId);
        viewerCountCacheService.clearViewerCount(streamId);

        Stream saved = streamRepository.save(stream);
        streamCacheService.cacheStream(saved);
        streamCacheService.evictAllLists();

        // Notify all subscribed viewers
        messagingTemplate.convertAndSend(
                "/topic/streams/" + streamId + "/chat",
                Map.of(
                        "type", "STREAM_ENDED",
                        "stream_id", streamId.toString(),
                        "timestamp", Instant.now().toString()
                )
        );

        log.info("Admin force-ended stream: {}", streamId);
        return StreamResponse.toPublicResponse(saved);
    }
}
