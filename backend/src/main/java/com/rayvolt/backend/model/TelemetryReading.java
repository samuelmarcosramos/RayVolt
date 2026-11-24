package com.rayvolt.backend.model;

import java.time.Instant;

public record TelemetryReading(Long scooterId, Instant timestamp, int battery, boolean locked,
        double speedKmh, Long hubId, double lat, double lon, PositionStatus status, boolean fault) {
}
