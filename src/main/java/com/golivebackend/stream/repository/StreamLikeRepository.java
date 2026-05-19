package com.golivebackend.stream.repository;

import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StreamLikeRepository extends JpaRepository<StreamLike, UUID> {

    boolean existsBySessionIdAndStream(String sessionId, Stream stream);

    void deleteBySessionIdAndStream(String sessionId, Stream stream);

    /**
     * Atomic increment — single DB operation, no race condition.
     */
    @Modifying
    @Query("UPDATE Stream s SET s.likesCount = s.likesCount + 1 WHERE s.streamId = :id")
    void incrementLikes(@Param("id") UUID id);

    /**
     * Atomic decrement with floor at 0 via GREATEST.
     * Prevents negative like counts under any circumstance.
     */
    @Modifying
    @Query("UPDATE Stream s SET s.likesCount = GREATEST(s.likesCount - 1, 0) WHERE s.streamId = :id")
    void decrementLikes(@Param("id") UUID id);
}