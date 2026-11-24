package com.rayvolt.backend.fleet.model;

import com.rayvolt.backend.errors.BadRequestException;
import com.rayvolt.backend.errors.ConflictException;
import java.util.Objects;

public record Scooter(Long id, String type, Long hubId, ScooterStatus status) {
    public Scooter {
        if ((id != null && id <= 0) || hubId == null || hubId <= 0) {
            throw new BadRequestException("Scooter and hub IDs must be positive numbers");
        }
        Objects.requireNonNull(status, "Status is required");
        if (type == null || type.isBlank()) {
            throw new BadRequestException("Scooter type is required");
        }
        type = type.strip();
    }

    public Scooter edit(String type, Long hubId) {
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
