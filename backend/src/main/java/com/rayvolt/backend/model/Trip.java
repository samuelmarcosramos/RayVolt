package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.ConflictException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "trips")
public class Trip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long riderId;
    private Long scooterId;
    private Instant startedAt;
    private Long startHubId;
    private double startOdometerKm;
    private int maxMinutes;
    private BigDecimal overtimeRate;
    private Instant endedAt;
    private Long endHubId;
    private Double endLat;
    private Double endLon;
    private Double kmRidden;
    private long overtimeMinutes;
    private BigDecimal overtimeCharge;
    private BigDecimal noHubCharge;

    protected Trip() { }

    public Trip(Long scooterId, Instant startedAt, Long startHubId, double startOdometerKm,
            int maxMinutes, BigDecimal overtimeRate) {
        if (scooterId == null || scooterId <= 0 || startedAt == null || startHubId == null
                || startHubId <= 0 || !Double.isFinite(startOdometerKm) || startOdometerKm < 0
                || maxMinutes <= 0 || overtimeRate == null || overtimeRate.signum() < 0) {
            throw new IllegalArgumentException("Valid trip start and pricing are required");
        }
        this.scooterId = scooterId;
        this.startedAt = startedAt;
        this.startHubId = startHubId;
        this.startOdometerKm = startOdometerKm;
        this.maxMinutes = maxMinutes;
        this.overtimeRate = overtimeRate;
    }

    public Trip(Long riderId, Long scooterId, Instant startedAt, Long startHubId, double startOdometerKm,
            int maxMinutes, BigDecimal overtimeRate) {
        this(scooterId, startedAt, startHubId, startOdometerKm, maxMinutes, overtimeRate);
        if (riderId == null || riderId <= 0) {
            throw new IllegalArgumentException("Rider ID must be positive");
        }
        this.riderId = riderId;
    }

    public Long getRiderId() { return riderId; }

    public void requireActive() {
        if (endedAt != null) {
            throw new ConflictException("Trip has already ended");
        }
    }

    public void end(Instant now, ScooterState state, Long hubId) {
        requireActive();
        if (now.isBefore(startedAt) || state.getOdometerKm() < startOdometerKm) {
            throw new ConflictException("Trip time or telemetry distance is inconsistent");
        }
        Duration extra = Duration.between(startedAt.plus(Duration.ofMinutes((long) maxMinutes + 5)), now);
        overtimeMinutes = extra.isNegative() || extra.isZero() ? 0
                : extra.toMinutes() + (extra.minusMinutes(extra.toMinutes()).isZero() ? 0 : 1);
        overtimeCharge = overtimeRate.multiply(BigDecimal.valueOf(overtimeMinutes));
        noHubCharge = hubId == null ? new BigDecimal("5.00") : new BigDecimal("0.00");
        endedAt = now;
        endHubId = hubId;
        endLat = state.getLat();
        endLon = state.getLon();
        kmRidden = state.getOdometerKm() - startOdometerKm;
    }

    public Long getId() { return id; }
    public Long getScooterId() { return scooterId; }
    public Instant getStartedAt() { return startedAt; }
    public Long getStartHubId() { return startHubId; }
    public Instant getEndedAt() { return endedAt; }
    public Long getEndHubId() { return endHubId; }
    public Double getEndLat() { return endLat; }
    public Double getEndLon() { return endLon; }
    public Double getKmRidden() { return kmRidden; }
    public long getOvertimeMinutes() { return overtimeMinutes; }
    public BigDecimal getOvertimeCharge() { return overtimeCharge; }
    public BigDecimal getNoHubCharge() { return noHubCharge; }
}
