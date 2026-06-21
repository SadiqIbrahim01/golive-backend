package com.golivebackend.stream.service;

import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamStatus;
import com.golivebackend.stream.repository.StreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViewerCountCacheService {

    private final StringRedisTemplate redisTemplate;
    private final StreamRepository streamRepository;

    private static final String KEY_PREFIX = "stream:viewer_count:";

    private String getKey(UUID streamId) {
        return KEY_PREFIX + streamId.toString();
    }

    /**
     * Atomically increments the viewer count in Redis.
     */
    public int incrementViewerCount(UUID streamId) {
        String key = getKey(streamId);
        Long count = redisTemplate.opsForValue().increment(key);
        int current = count != null ? count.intValue() : 1;
        log.debug("Redis increment viewer count for stream {}: {}", streamId, current);
        return current;
    }

    /**
     * Atomically decrements the viewer count in Redis.
     * Enforces floor of 0.
     */
    public int decrementViewerCount(UUID streamId) {
        String key = getKey(streamId);
        Long count = redisTemplate.opsForValue().decrement(key);
        int current = count != null ? count.intValue() : 0;
        if (current < 0) {
            redisTemplate.opsForValue().set(key, "0");
            current = 0;
        }
        log.debug("Redis decrement viewer count for stream {}: {}", streamId, current);
        return current;
    }

    /**
     * Retrieves viewer count from Redis, falling back to database.
     */
    public int getViewerCount(UUID streamId) {
        String key = getKey(streamId);
        String cachedValue = redisTemplate.opsForValue().get(key);
        if (cachedValue != null) {
            try {
                return Integer.parseInt(cachedValue);
            } catch (NumberFormatException e) {
                log.error("Failed to parse viewer count from cache: {}", cachedValue);
            }
        }

        // Fallback to database
        int dbCount = streamRepository.getViewerCount(streamId);
        redisTemplate.opsForValue().set(key, String.valueOf(dbCount));
        return dbCount;
    }

    /**
     * Syncs a specific stream's count to the database immediately.
     */
    @Transactional
    public void syncViewerCountToDatabase(UUID streamId) {
        String key = getKey(streamId);
        String cachedValue = redisTemplate.opsForValue().get(key);
        if (cachedValue != null) {
            try {
                int count = Integer.parseInt(cachedValue);
                streamRepository.updateViewerCount(streamId, count);
                log.info("Synced viewer count of {} (count={}) to DB", streamId, count);
            } catch (NumberFormatException e) {
                log.error("Failed to parse viewer count from cache for sync: {}", cachedValue);
            }
        }
    }

    /**
     * Deletes key from Redis when stream has ended.
     */
    public void clearViewerCount(UUID streamId) {
        String key = getKey(streamId);
        redisTemplate.delete(key);
        log.debug("Cleared Redis viewer count key for stream {}", streamId);
    }

    /**
     * Scheduled background synchronization task.
     * Runs every 10 seconds.
     */
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void syncViewerCountsToDatabase() {
        log.debug("Starting periodic Redis-to-DB viewer counts synchronization...");
        List<Stream> liveStreams = streamRepository.findAllByStatus(StreamStatus.LIVE);
        if (liveStreams.isEmpty()) {
            return;
        }

        for (Stream stream : liveStreams) {
            UUID streamId = stream.getStreamId();
            String key = getKey(streamId);
            String cachedValue = redisTemplate.opsForValue().get(key);
            if (cachedValue != null) {
                try {
                    int count = Integer.parseInt(cachedValue);
                    streamRepository.updateViewerCount(streamId, count);
                } catch (NumberFormatException e) {
                    log.error("Failed to parse viewer count from cache for stream {} during schedule sync: {}", streamId, cachedValue);
                }
            }
        }
        log.debug("Periodic viewer counts synchronization completed.");
    }
}
