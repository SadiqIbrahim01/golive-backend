package com.golivebackend.stream.service;

import com.golivebackend.stream.dto.StreamRequest;
import com.golivebackend.stream.dto.StreamResponse;
import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamStatus;
import com.golivebackend.stream.repository.StreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for stream management.
 *
 * LAYERS THIS CLASS SITS BETWEEN:
 *   StreamController (HTTP layer)  →  StreamService  →  StreamRepository (DB)
 *
 * RULES FOR THIS CLASS:
 *   ✅ All business logic lives here
 *   ✅ Returns DTOs to the controller — never entities
 *   ✅ Accepts DTOs from the controller — never raw HTTP params
 *   ❌ Never handles HTTP concerns (status codes, headers)
 *   ❌ Never writes SQL or calls JPA directly (that's the repository)
 *
 * @Service marks this as a Spring-managed bean and signals intent.
 *
 * @RequiredArgsConstructor (Lombok): generates a constructor for all
 * 'final' fields. Spring sees one constructor → uses it for injection.
 * This is constructor injection — the correct approach because:
 *   - Dependencies are explicit and required at construction time
 *   - The object is fully initialised or not at all
 *   - Easy to test — just pass a mock to the constructor
 *
 * @Slf4j (Lombok): injects a Logger named 'log'.
 * Equivalent to: private static final Logger log =
 *                    LoggerFactory.getLogger(StreamService.class);
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final StreamRepository streamRepository;

    /**
     * Creates a new stream session.
     *
     * WHAT HAPPENS HERE:
     *   1. Generate a unique roomName for LiveKit
     *   2. Generate a secret hostKey
     *   3. Build the Stream entity via its factory method
     *   4. Persist to PostgreSQL
     *   5. Return a host response (includes hostKey — returned once only)
     *
     * @Transactional: wraps this method in a DB transaction.
     * If anything throws an exception, the transaction rolls back.
     * The stream is either fully created or not at all.
     *
     * WHY NOT @Transactional ON THE CLASS?
     * Annotating the class makes every method transactional by default.
     * Explicit annotation per method is more intentional — readOnly
     * queries use @Transactional(readOnly = true) which is a meaningful
     * DB-level optimisation (no flush, no dirty checking).
     */
    @Transactional
    public StreamResponse createStream(StreamRequest request) {
        log.info("Creating new stream with title: {}", request.title());

        String roomName = generateRoomName();
        String hostKey  = generateHostKey();

        Stream stream = Stream.create(
                roomName,
                hostKey,
                request.title()
        );

        Stream saved = streamRepository.save(stream);

        log.info("Stream created successfully. streamId={}, roomName={}",
                saved.getStreamId(), saved.getRoomName());

        /*
         * toHostResponse: includes hostKey and hostUrl.
         * This is the ONLY place we call toHostResponse.
         * Every other method in this service uses toPublicResponse.
         */
        return StreamResponse.toHostResponse(saved);
    }

    /**
     * Retrieves a stream by its public ID.
     *
     * @Transactional(readOnly = true):
     * Tells Hibernate this transaction will not modify data.
     * Hibernate skips dirty checking (comparing current vs original state)
     * on every entity in the session — meaningful performance gain
     * when reading large result sets.
     * At the DB level, some databases can route readOnly transactions
     * to read replicas automatically.
     *
     * @throws StreamNotFoundException if no stream with this ID exists.
     * The exception handler (Step 2.4) maps this to HTTP 404.
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
     * Returns all currently live streams.
     *
     * Returns a List<StreamResponse> — never a List<Stream>.
     * The controller never touches an entity. Ever.
     */
    @Transactional(readOnly = true)
    public List<StreamResponse> findLiveStreams() {
        log.debug("Fetching all LIVE streams");

        return streamRepository
                .findLiveStreamsOrderedByStartTime(StreamStatus.LIVE)
                .stream()
                .map(StreamResponse::toPublicResponse)
                .toList();
        /*
         * .toList() returns an unmodifiable List (Java 16+).
         * Callers receive a snapshot — they can't add/remove from it.
         * Matches our intent: this is a read-only query result.
         */
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    /**
     * Generates a unique LiveKit room name.
     *
     * Format: "room-{UUID}"
     * The "room-" prefix makes it obvious in LiveKit's dashboard
     * that this identifier is a room (not a participant or token).
     *
     * UUID.randomUUID() is cryptographically random (SecureRandom).
     * Collision probability is negligible at our scale.
     *
     * We strip hyphens because some LiveKit SDK versions handle
     * hyphens inconsistently in room names.
     */
    private String generateRoomName() {
        return "room-" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generates a cryptographically secure host key.
     *
     * UUID v4 = 122 bits of randomness from SecureRandom.
     * Sufficient entropy for a short-lived session secret.
     *
     * UPGRADE PATH:
     * For production with persistent host accounts, replace this
     * with a bcrypt-hashed token stored in the DB.
     * The raw token is given to the host; the hash is stored.
     * Verification: bcrypt.matches(incomingKey, storedHash).
     */
    private String generateHostKey() {
        return UUID.randomUUID().toString();
    }
}