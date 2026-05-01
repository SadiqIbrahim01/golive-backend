package com.golivebackend.livekit.model;

/**
 * Represents the role of a participant in a LiveKit room.
 *
 * This enum lives in the livekit module because it is a concept
 * that belongs to media session participation — not to the
 * stream domain (which only knows CREATED/LIVE/ENDED).
 *
 * Keeping it here enforces module boundaries:
 * the stream module has no knowledge of LiveKit roles.
 */
public enum ParticipantRole {

    /**
     * Can publish (screen share, camera, mic) and subscribe.
     * Requires hostKey verification before token is issued.
     */
    HOST,

    /**
     * Can only subscribe (watch/listen).
     * No verification required — anyone with the watch URL can get this.
     */
    VIEWER
}