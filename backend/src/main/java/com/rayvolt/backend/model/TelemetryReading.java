package com.rayvolt.backend.model;

import java.time.Instant;

public record TelemetryReading(Long scooterId, Instant timestamp, int battery, boolean locked,
        double speedKmh, Long hubId, double lat, double lon, PositionStatus status, boolean fault) {

    public TelemetryReading {
        if (scooterId == null || timestamp == null || status == null) {
            throw new IllegalArgumentException("scooterId, timestamp and status are required");
        }
        if (battery < 0 || battery > 100) {
            throw new IllegalArgumentException("battery must be between 0 and 100");
        }
        if (speedKmh < 0) {
            throw new IllegalArgumentException("speedKmh must not be negative");
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("lat or lon out of range");
        }
    }
}
