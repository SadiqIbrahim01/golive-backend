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

    @Query("""
            SELECT s FROM Stream s
            WHERE s.status = 'LIVE'
            AND (
                LOWER(s.category) = LOWER(:category)
                OR LOWER(s.category) LIKE LOWER(CONCAT('%', :category, '%'))
            )
            ORDER BY s.startedAt DESC
            """)
    List<Stream> findLiveStreamsByCategory(@Param("category") String category);

    @Query("""
            SELECT s FROM Stream s
            WHERE s.status = 'LIVE'
            ORDER BY s.viewerCount DESC, s.startedAt DESC
            """)
    List<Stream> findTrendingStreams();

    // ─── VIEWER COUNT ─────────────────────────────────────────────────────────

    @Query("SELECT s.viewerCount FROM Stream s WHERE s.streamId = :id")
    int getViewerCount(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Stream s SET s.viewerCount = :viewerCount WHERE s.streamId = :id")
    void updateViewerCount(@Param("id") UUID id, @Param("viewerCount") int viewerCount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Stream s SET s.viewerCount = s.viewerCount + 1 WHERE s.streamId = :id")
    void incrementViewerCount(@Param("id") UUID id);

    /**
     * FIXED: was JPQL with GREATEST() — not valid JPQL.
     * nativeQuery = true sends raw SQL directly to PostgreSQL.
     * GREATEST(viewer_count - 1, 0) ensures the count never goes below zero.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE streams SET viewer_count = GREATEST(viewer_count - 1, 0) WHERE stream_id = :id",
            nativeQuery = true
    )
    void decrementViewerCount(@Param("id") UUID id);

    // ─── LIKES ────────────────────────────────────────────────────────────────

    @Modifying
    @Query("UPDATE Stream s SET s.likesCount = s.likesCount + 1 WHERE s.streamId = :id")
    void incrementLikes(@Param("id") UUID id);

    /**
     * FIXED: same issue as decrementViewerCount.
     * GREATEST(likes_count - 1, 0) prevents negative like counts
     * under any concurrent decrement scenario.
     */
    @Modifying
    @Query(
            value = "UPDATE streams SET likes_count = GREATEST(likes_count - 1, 0) WHERE stream_id = :id",
            nativeQuery = true
    )
    void decrementLikes(@Param("id") UUID id);

    /**
     * Resets viewer count for all LIVE streams.
     * Called on application startup to correct stale counts
     * left over from a previous server instance.
     */
    @Modifying
    @Query("UPDATE Stream s SET s.viewerCount = 0 WHERE s.status = 'LIVE'")
    void resetViewerCountsForLiveStreams();
}