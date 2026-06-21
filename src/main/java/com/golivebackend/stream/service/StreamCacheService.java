package com.golivebackend.stream.service;

import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.repository.StreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StreamRepository streamRepository;

    private static final String ID_PREFIX = "stream:id:";
    private static final String HOSTKEY_PREFIX = "stream:hostkey:";
    private static final long CACHE_TTL_MINUTES = 60; // Cache for 60 minutes

    private String getIdKey(UUID streamId) {
        return ID_PREFIX + streamId.toString();
    }

    private String getHostKeyKey(String hostKey) {
        return HOSTKEY_PREFIX + hostKey;
    }

    /**
     * Finds stream by public streamId from Redis cache. Falls back to database.
     */
    public Optional<Stream> findByStreamId(UUID streamId) {
        if (streamId == null) return Optional.empty();
        String key = getIdKey(streamId);
        try {
            Stream cached = (Stream) redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Cache hit for streamId: {}", streamId);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.error("Failed to retrieve stream from Redis cache for streamId: {}", streamId, e);
        }

        log.debug("Cache miss for streamId: {}", streamId);
        Optional<Stream> dbResult = streamRepository.findByStreamId(streamId);
        dbResult.ifPresent(this::cacheStream);
        return dbResult;
    }

    /**
     * Finds stream by hostKey from Redis cache. Falls back to database.
     */
    public Optional<Stream> findByHostKey(String hostKey) {
        if (hostKey == null || hostKey.isBlank()) return Optional.empty();
        String key = getHostKeyKey(hostKey);
        try {
            Stream cached = (Stream) redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Cache hit for hostKey: {}", hostKey);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.error("Failed to retrieve stream from Redis cache for hostKey: {}", hostKey, e);
        }

        log.debug("Cache miss for hostKey: {}", hostKey);
        Optional<Stream> dbResult = streamRepository.findByHostKey(hostKey);
        dbResult.ifPresent(this::cacheStream);
        return dbResult;
    }

    /**
     * Caches a stream under both its streamId and hostKey keys in Redis.
     */
    public void cacheStream(Stream stream) {
        if (stream == null) return;
        try {
            String idKey = getIdKey(stream.getStreamId());
            redisTemplate.opsForValue().set(idKey, stream, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

            if (stream.getHostKey() != null && !stream.getHostKey().isBlank()) {
                String hostKey = getHostKeyKey(stream.getHostKey());
                redisTemplate.opsForValue().set(hostKey, stream, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            log.debug("Successfully cached stream: {}", stream.getStreamId());
        } catch (Exception e) {
            log.error("Failed to cache stream {} in Redis", stream.getStreamId(), e);
        }
    }

    /**
     * Evicts a stream from the Redis cache.
     */
    public void evictStream(Stream stream) {
        if (stream == null) return;
        try {
            String idKey = getIdKey(stream.getStreamId());
            redisTemplate.delete(idKey);

            if (stream.getHostKey() != null && !stream.getHostKey().isBlank()) {
                String hostKey = getHostKeyKey(stream.getHostKey());
                redisTemplate.delete(hostKey);
            }
            log.debug("Successfully evicted stream from cache: {}", stream.getStreamId());
        } catch (Exception e) {
            log.error("Failed to evict stream {} from Redis cache", stream.getStreamId(), e);
        }
    }

    /**
     * Retrieves a cached list of StreamResponse objects from Redis.
     */
    @SuppressWarnings("unchecked")
    public java.util.List<com.golivebackend.stream.dto.StreamResponse> getCachedList(String key) {
        try {
            return (java.util.List<com.golivebackend.stream.dto.StreamResponse>) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Failed to retrieve list from Redis cache for key: {}", key, e);
            return null;
        }
    }

    /**
     * Caches a list of StreamResponse objects in Redis.
     */
    public void cacheList(String key, java.util.List<com.golivebackend.stream.dto.StreamResponse> list) {
        if (list == null) return;
        try {
            redisTemplate.opsForValue().set(key, list, 5, TimeUnit.MINUTES); // Cache lists for 5 minutes
            log.debug("Successfully cached list under key: {}", key);
        } catch (Exception e) {
            log.error("Failed to cache list under key: {} in Redis", key, e);
        }
    }

    /**
     * Evicts all cached stream lists matching the pattern stream:list:*.
     */
    public void evictAllLists() {
        try {
            java.util.Set<String> keys = redisTemplate.keys("stream:list:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Evicted all stream list caches: {}", keys);
            }
        } catch (Exception e) {
            log.error("Failed to evict stream lists from Redis cache", e);
        }
    }
}
