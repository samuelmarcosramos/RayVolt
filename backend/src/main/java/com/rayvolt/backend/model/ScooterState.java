package com.rayvolt.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "scooter_state")
public class ScooterState {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    @Id
    private Long scooterId;
    private Instant readingAt;
    private Instant receivedAt;
    private int battery;
    private boolean locked;
    private double speedKmh;
    private Long hubId;
    private double lat;
    private double lon;
    @Enumerated(EnumType.STRING)
    private PositionStatus positionStatus;
    private boolean fault;
    private double odometerKm;

    protected ScooterState() {
    }

    public ScooterState(TelemetryReading reading, Instant receivedAt) {
        this.scooterId = reading.scooterId();
        copy(reading, receivedAt);
    }

    public boolean isNewer(TelemetryReading reading) {
        return reading.timestamp().isAfter(readingAt);
    }

    public void update(TelemetryReading reading, Instant receivedAt) {
        if (positionStatus == PositionStatus.MOVING) {
            odometerKm += distanceKm(lat, lon, reading.lat(), reading.lon());
        }
        copy(reading, receivedAt);
    }

    public boolean markFault() {
        if (fault) {
            return false;
        }
        fault = true;
        return true;
    }

    private void copy(TelemetryReading reading, Instant receivedAt) {
        this.readingAt = reading.timestamp();
        this.receivedAt = receivedAt;
        this.battery = reading.battery();
        this.locked = reading.locked();
        this.speedKmh = reading.speedKmh();
        this.hubId = reading.hubId();
        this.lat = reading.lat();
        this.lon = reading.lon();
        this.positionStatus = reading.status();
        this.fault = reading.fault();
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    public Long getScooterId() {
        return scooterId;
    }

    public Instant getReadingAt() {
        return readingAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public int getBattery() {
        return battery;
    }

    public boolean isLocked() {
        return locked;
    }

    public double getSpeedKmh() {
        return speedKmh;
    }

    public Long getHubId() {
        return hubId;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public PositionStatus getPositionStatus() {
        return positionStatus;
    }

    public boolean isFault() {
        return fault;
    }

    public double getOdometerKm() {
        return odometerKm;
    }
}
