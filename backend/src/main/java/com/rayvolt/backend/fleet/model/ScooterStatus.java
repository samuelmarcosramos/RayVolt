package com.rayvolt.backend.fleet.model;

public enum ScooterStatus {
    AVAILABLE, RESERVED, IN_USE, MAINTENANCE, RETIRED;

    public boolean canTransitionTo(ScooterStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case AVAILABLE, RESERVED -> true;
            case IN_USE -> target == AVAILABLE || target == MAINTENANCE;
            case MAINTENANCE -> target == AVAILABLE || target == RETIRED;
            case RETIRED -> false;
        };
    }
}
