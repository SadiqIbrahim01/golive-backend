package com.golivebackend.stream.dto;

import com.golivebackend.stream.model.Stream;
import com.golivebackend.stream.model.StreamStatus;
import com.golivebackend.stream.model.StreamType;

import java.time.Instant;
import java.util.UUID;

public record StreamResponse(
        UUID streamId,
        String title,
        String category,
        StreamType streamType,
        StreamStatus status,
        String watchUrl,
        String hostUrl,
        String hostKey,
        int viewerCount,
        int likesCount,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt
) implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    public static StreamResponse toPublicResponse(Stream stream) {
        return new StreamResponse(
                stream.getStreamId(),
                stream.getTitle(),
                stream.getCategory(),
                stream.getStreamType(),
                stream.getStatus(),
                buildWatchUrl(stream.getStreamId()),
                null,
                null,
                stream.getViewerCount(),
                stream.getLikesCount(),
                stream.getCreatedAt(),
                stream.getStartedAt(),
                stream.getEndedAt()
        );
    }

    public static StreamResponse toPublicResponseWithViewerCount(Stream stream, int viewerCount) {
        return new StreamResponse(
                stream.getStreamId(),
                stream.getTitle(),
                stream.getCategory(),
                stream.getStreamType(),
                stream.getStatus(),
                buildWatchUrl(stream.getStreamId()),
                null,
                null,
                viewerCount,
                stream.getLikesCount(),
                stream.getCreatedAt(),
                stream.getStartedAt(),
                stream.getEndedAt()
        );
    }

    public static StreamResponse toHostResponse(Stream stream) {
        return new StreamResponse(
                stream.getStreamId(),
                stream.getTitle(),
                stream.getCategory(),
                stream.getStreamType(),
                stream.getStatus(),
                buildWatchUrl(stream.getStreamId()),
                buildHostUrl(stream.getStreamId()),
                stream.getHostKey(),
                stream.getViewerCount(),
                stream.getLikesCount(),
                stream.getCreatedAt(),
                stream.getStartedAt(),
                stream.getEndedAt()
        );
    }

    private static String buildWatchUrl(UUID streamId) {
        return "http://localhost:3000/watch/" + streamId;
    }

    private static String buildHostUrl(UUID streamId) {
        return "http://localhost:3000/host/" + streamId;
    }
}