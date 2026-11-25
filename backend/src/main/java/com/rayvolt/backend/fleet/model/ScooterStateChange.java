package com.rayvolt.backend.fleet.model;

import java.time.Instant;

public record ScooterStateChange(Long id, Long scooterId, ScooterStatus fromStatus,
        ScooterStatus toStatus, Instant changedAt, StateChangeCause cause) {
}
