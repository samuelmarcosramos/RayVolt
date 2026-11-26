package com.rayvolt.backend.model;

public enum ScooterStatus {
    AVAILABLE, RESERVED, IN_USE, MAINTENANCE, RETIRED;

    public boolean canTransitionTo(ScooterStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case AVAILABLE -> true;
            case RESERVED -> target == AVAILABLE || target == IN_USE || target == MAINTENANCE;
            case IN_USE -> target == AVAILABLE || target == MAINTENANCE;
            case MAINTENANCE -> target == AVAILABLE || target == RETIRED;
            case RETIRED -> false;
        };
    }
}
