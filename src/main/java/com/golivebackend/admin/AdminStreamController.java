package com.golivebackend.admin;

import com.golivebackend.stream.dto.StreamResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only stream management endpoints.
 *
 * All endpoints in this controller require ROLE_ADMIN.
 * Access is enforced at two levels:
 *   1. SecurityConfig.authorizeHttpRequests() — /admin/** requires hasRole("ADMIN")
 *   2. @PreAuthorize on each method — method-level defence in depth
 *
 * WHY BOTH?
 * SecurityConfig is the primary enforcement gate.
 * @PreAuthorize provides a second layer so that even if the URL mapping
 * is accidentally widened or the path changes, the method itself is still guarded.
 * This is the "defence in depth" principle.
 */
@Slf4j
@RestController
@RequestMapping("/admin/streams")
@RequiredArgsConstructor
public class AdminStreamController {

    private final AdminStreamService adminStreamService;

    /**
     * Returns all streams regardless of status.
     * Ordered by createdAt descending.
     *
     * GET /admin/streams/all
     * Authorization: Bearer <adminToken>
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StreamResponse>> getAllStreams() {
        log.debug("Admin: GET /admin/streams/all");
        return ResponseEntity.ok(adminStreamService.getAllStreams());
    }

    /**
     * Returns aggregate dashboard statistics.
     * Includes totalStreams, liveNow, endedToday, totalViewers.
     *
     * GET /admin/streams/stats
     * Authorization: Bearer <adminToken>
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminStreamStatsResponse> getStats() {
        log.debug("Admin: GET /admin/streams/stats");
        return ResponseEntity.ok(adminStreamService.getStats());
    }

    /**
     * Force-ends a live stream without requiring the host key.
     * Admin JWT serves as the authority.
     *
     * DELETE /admin/streams/{id}/force-end
     * Authorization: Bearer <adminToken>
     *
     * @param id UUID of the stream to end
     * @return 200 with the updated stream on success
     *         404 if the stream doesn't exist
     *         409 if the stream is not currently LIVE
     */
    @DeleteMapping("/{id}/force-end")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StreamResponse> forceEndStream(@PathVariable UUID id) {
        log.info("Admin: DELETE /admin/streams/{}/force-end", id);
        return ResponseEntity.ok(adminStreamService.forceEndStream(id));
    }
}
