package com.golivebackend.stream.repository;

import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for Stream entities.
 *
 * WHY EXTENDS JpaRepository AND NOT CrudRepository?
 * JpaRepository extends CrudRepository and adds:
 *   - findAll(Sort) and findAll(Pageable) — for sorted/paginated queries
 *   - saveAll() — batch inserts
 *   - flush() — force immediate DB write within a transaction
 *
 * We extend JpaRepository so pagination is available when we need it
 * (Day 6 production hardening) without changing the interface.
 *
 * Type parameters: <Entity, PrimaryKeyType>
 *   Entity = Stream
 *   PrimaryKeyType = UUID (must match the @Id field type on Stream)
 *
 * @Repository marks this as a Spring-managed bean AND tells Spring to
 * translate JDBC exceptions into Spring's DataAccessException hierarchy.
 * Without it, a PostgreSQL constraint violation would surface as a raw
 * SQLException — harder to handle cleanly in the service layer.
 */
@Repository
public interface StreamRepository extends JpaRepository<Stream, UUID> {

    /**
     * Find a stream by its public ID.
     *
     * Returns Optional<Stream> — not Stream directly.
     *
     * WHY OPTIONAL?
     * A stream with the given ID might not exist. Returning null forces
     * every caller to remember to null-check — and someone will forget.
     * Optional makes the "might not exist" case explicit in the type system.
     * The service layer calls .orElseThrow() and decides what error to raise.
     *
     * HOW SPRING DERIVES THIS:
     * "findBy" → SELECT WHERE
     * "StreamId" → the field name on the Stream entity (streamId)
     * Spring reads the entity, finds the field, generates the query.
     *
     * Generated SQL:
     *   SELECT * FROM streams WHERE stream_id = ?
     */
    Optional<Stream> findByStreamId(UUID streamId);

    /**
     * Find all streams with a given status.
     *
     * Used by the home page to list currently LIVE streams.
     *
     * WHY List AND NOT Page?
     * For MVP with a small number of concurrent streams, List is fine.
     * When concurrent streams grow, we'd change this to:
     *   Page<Stream> findAllByStatus(StreamStatus status, Pageable pageable);
     * and pass PageRequest.of(0, 20, Sort.by("createdAt").descending()).
     *
     * Generated SQL:
     *   SELECT * FROM streams WHERE status = ?
     */
    List<Stream> findAllByStatus(StreamStatus status);

    /**
     * Check whether a stream with the given hostKey exists.
     *
     * Used as a fast existence check before loading the full entity.
     * Returns a boolean — cheaper than fetching the whole row when
     * you only need to know "does this exist?"
     *
     * Generated SQL:
     *   SELECT COUNT(*) > 0 FROM streams WHERE host_key = ?
     */
    boolean existsByHostKey(String hostKey);

    /**
     * Find a stream by its hostKey.
     *
     * Used on lifecycle endpoints (start/end) where the host
     * proves identity by providing the hostKey.
     *
     * We look up by hostKey rather than by streamId + hostKey separately
     * because: if the hostKey matches, we already know it's the right stream
     * (hostKey is unique per stream by design — a UUID).
     *
     * Generated SQL:
     *   SELECT * FROM streams WHERE host_key = ?
     */
    Optional<Stream> findByHostKey(String hostKey);

    /**
     * Find all LIVE streams ordered by most recently started.
     *
     * WHY A CUSTOM @QUERY HERE?
     * Spring could derive: findAllByStatusOrderByStartedAtDesc(StreamStatus)
     * but that method name is 47 characters — unclear and brittle.
     * When derivation produces unreadable names, write explicit JPQL.
     *
     * JPQL vs SQL:
     *   SQL   → talks to tables and columns  (streams, started_at)
     *   JPQL  → talks to entities and fields (Stream,  startedAt)
     * JPQL is database-agnostic — if you ever moved from PostgreSQL
     * to another DB, this query works unchanged.
     *
     * @Param("status") binds the method parameter to the :status
     * placeholder in the JPQL string.
     */
    @Query("""
            SELECT s FROM Stream s
            WHERE s.status = :status
            ORDER BY s.startedAt DESC
            """)
    List<Stream> findLiveStreamsOrderedByStartTime(
            @Param("status") StreamStatus status
    );
}