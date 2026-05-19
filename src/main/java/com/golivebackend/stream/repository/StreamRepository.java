package com.golivebackend.stream.repository;

import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StreamRepository extends JpaRepository<Stream, UUID> {

    // =========================================================
    // LOOKUPS
    // =========================================================

    /**
     * Find a stream by its public UUID.
     * Returns Optional — caller decides what to do if not found.
     */
    Optional<Stream> findByStreamId(UUID streamId);

    /**
     * Find a stream by its hostKey.
     * Used to validate host identity on lifecycle endpoints.
     */
    Optional<Stream> findByHostKey(String hostKey);

    /**
     * Check whether a stream with the given hostKey exists.
     * Cheaper than loading the full entity when you only need existence.
     */
    boolean existsByHostKey(String hostKey);

    // =========================================================
    // LISTINGS
    // =========================================================

    /**
     * Returns all streams with the given status.
     * Used internally — prefer the JPQL query below for LIVE listings.
     */
    List<Stream> findAllByStatus(StreamStatus status);

    /**
     * Returns all LIVE streams ordered by most recently started.
     * Used by GET /api/streams (no query param).
     */
    @Query("""
            SELECT s FROM Stream s
            WHERE s.status = :status
            ORDER BY s.startedAt DESC
            """)
    List<Stream> findLiveStreamsOrderedByStartTime(
            @Param("status") StreamStatus status
    );

    // =========================================================
    // SEARCH
    // =========================================================

    /**
     * Case-insensitive search across title and category.
     * Only returns LIVE streams.
     * Used by GET /api/streams?query={value}
     *
     * :query must be passed with % wildcards from the service layer.
     * Example: service passes "%java%" → matches "Java Coding"
     */
    @Query("""
            SELECT s FROM Stream s
            WHERE s.status = 'LIVE'
            AND (
                LOWER(s.title)    LIKE LOWER(:query)
                OR
                LOWER(s.category) LIKE LOWER(:query)
            )
            ORDER BY s.startedAt DESC
            """)
    List<Stream> searchLiveStreams(@Param("query") String query);

    // =========================================================
    // VIEWER COUNT — atomic operations
    // =========================================================

    /**
     * Atomically increments viewer count by 1.
     * Called on WebSocket subscribe event.
     */
    @Modifying
    @Query("UPDATE Stream s SET s.viewerCount = s.viewerCount + 1 WHERE s.streamId = :id")
    void incrementViewerCount(@Param("id") UUID id);

    /**
     * Atomically decrements viewer count by 1, floor at 0.
     * GREATEST() prevents negative values at the DB level.
     * Called on WebSocket disconnect event.
     */
    @Modifying
    @Query("UPDATE Stream s SET s.viewerCount = GREATEST(s.viewerCount - 1, 0) WHERE s.streamId = :id")
    void decrementViewerCount(@Param("id") UUID id);
}