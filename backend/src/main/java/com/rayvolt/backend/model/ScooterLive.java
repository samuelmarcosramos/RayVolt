package com.rayvolt.backend.model;

import java.time.Instant;

public record ScooterLive(Long scooterId, ScooterStatus status, Integer battery, Double lat, Double lon,
        PositionStatus positionStatus, boolean fault, Instant lastSeen) {

    public static ScooterLive of(Scooter scooter, ScooterState state) {
        if (state == null) {
            return new ScooterLive(scooter.getId(), scooter.getStatus(), null, null, null, null, false, null);
        }
        return new ScooterLive(scooter.getId(), scooter.getStatus(), state.getBattery(), state.getLat(),
                state.getLon(), state.getPositionStatus(), state.isFault(), state.getReceivedAt());
    }
}
