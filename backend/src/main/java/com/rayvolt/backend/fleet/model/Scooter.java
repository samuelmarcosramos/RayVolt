package com.rayvolt.backend.fleet.model;

import com.rayvolt.backend.common.exception.BadRequestException;
import com.rayvolt.backend.common.exception.ConflictException;
import java.util.Objects;

public record Scooter(Long id, String type, Long hubId, ScooterStatus status, int batteryPercent) {
    public Scooter {
        if ((id != null && id <= 0) || hubId == null || hubId <= 0) {
            throw new BadRequestException("Scooter and hub IDs must be positive numbers");
        }
        if (batteryPercent < 0 || batteryPercent > 100) {
            throw new BadRequestException("Battery percentage must be between 0 and 100");
        }
        Objects.requireNonNull(status, "Status is required");
        if (type == null || type.isBlank()) {
            throw new BadRequestException("Scooter type is required");
        }
        type = type.strip();
    }

    public Scooter(Long id, String type, Long hubId, ScooterStatus status) {
        this(id, type, hubId, status, 100);
    }

    public Scooter edit(String type, Long hubId) {
        requireEditable();
        return new Scooter(id, type, hubId, status, batteryPercent);
    }

    public Scooter retire() {
        requireEditable();
        return new Scooter(id, type, hubId, ScooterStatus.RETIRED, batteryPercent);
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
