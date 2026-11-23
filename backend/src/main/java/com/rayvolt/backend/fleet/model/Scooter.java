package com.rayvolt.backend.fleet.model;

import com.rayvolt.backend.common.exception.BadRequestException;
import com.rayvolt.backend.common.exception.ConflictException;
import java.util.Objects;
import java.util.UUID;

public record Scooter(UUID id, String type, UUID hubId, ScooterStatus status) {
    public Scooter {
        Objects.requireNonNull(id, "Scooter ID is required");
        Objects.requireNonNull(hubId, "Hub ID is required");
        Objects.requireNonNull(status, "Status is required");
        if (type == null || type.isBlank()) {
            throw new BadRequestException("Scooter type is required");
        }
        type = type.strip();
    }

    public Scooter edit(String type, UUID hubId) {
        requireEditable();
        return new Scooter(id, type, hubId, status);
    }

    public Scooter retire() {
        requireEditable();
        return new Scooter(id, type, hubId, ScooterStatus.RETIRED);
    }

    public boolean visibleToUsers() {
        return status != ScooterStatus.RETIRED;
    }

    private void requireEditable() {
        if (status == ScooterStatus.IN_USE || status == ScooterStatus.RESERVED
                || status == ScooterStatus.RETIRED) {
            throw new ConflictException("An in-use, reserved or retired scooter cannot be changed");
        }
    }
}
