package com.golivebackend.admin;

/**
 * Aggregate statistics returned by GET /admin/streams/stats.
 *
 * All counts are computed in the service layer — no DB view needed.
 *
 * totalStreams    — total number of stream records in the DB (any status)
 * liveNow        — streams currently with status = LIVE
 * endedToday     — streams that ended since midnight UTC today
 * totalViewers   — sum of viewerCount across all LIVE streams
 */
public record AdminStreamStatsResponse(
        long totalStreams,
        long liveNow,
        long endedToday,
        long totalViewers
) {}
