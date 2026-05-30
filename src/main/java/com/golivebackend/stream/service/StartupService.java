package com.golivebackend.stream.service;

import com.golivebackend.stream.repository.StreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs once immediately after the application context is fully started.
 *
 * WHY ApplicationRunner AND NOT @PostConstruct?
 * @PostConstruct fires during bean initialisation — before the full
 * application context is ready. Database transactions may not be
 * fully available at that point.
 * ApplicationRunner fires after the entire context is ready and the
 * app is accepting requests — safe for DB operations.
 *
 * WHY RESET VIEWER COUNTS ON STARTUP?
 * Viewer counts are tracked via an in-memory ConcurrentHashMap.
 * On restart, that map is empty. Active WebSocket clients reconnect
 * and re-subscribe — incrementing viewer counts correctly.
 * But the sessions that were active before the restart never fire
 * a disconnect event — their counts are never decremented.
 * Result without this fix: viewer counts inflate permanently after
 * every restart.
 *
 * By resetting all LIVE stream viewer counts to 0 on startup,
 * counts rebuild correctly from scratch as clients reconnect.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupService implements ApplicationRunner {

    private final StreamRepository streamRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Startup: resetting viewer counts for all LIVE streams");

        streamRepository.resetViewerCountsForLiveStreams();

        log.info("Startup: viewer count reset complete — " +
                "counts will rebuild as viewers reconnect");
    }
}