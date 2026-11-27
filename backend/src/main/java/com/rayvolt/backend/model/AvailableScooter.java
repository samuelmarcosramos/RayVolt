package com.rayvolt.backend.model;

public record AvailableScooter(Long id, String type, Long hubId, ScooterStatus status, Integer batteryPercent) {
}
