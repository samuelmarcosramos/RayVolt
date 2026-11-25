package com.rayvolt.backend.fleet.model;

import com.rayvolt.backend.common.exception.BadRequestException;
import com.rayvolt.backend.common.exception.ConflictException;
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
        return changeStatus(ScooterStatus.RETIRED);
    }

    public Scooter changeStatus(ScooterStatus target) {
        if (target == null) {
            throw new BadRequestException("Target state is required");
        }
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("Cannot change scooter state from " + status + " to " + target);
        }
        return new Scooter(id, type, hubId, target);
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
