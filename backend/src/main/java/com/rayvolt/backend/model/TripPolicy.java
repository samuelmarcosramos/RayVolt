package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.errors.ForbiddenException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "trip_policies")
public class TripPolicy {
    @Id @Enumerated(EnumType.STRING) private RiderCategory category;
    private LocalTime allowedFrom;
    private LocalTime allowedUntil;
    private String timeZone;
    private int minimumBattery;
    private int maxMinutes;
    private BigDecimal overtimeRate;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trip_policy_types", joinColumns = @JoinColumn(name = "category"))
    @Column(name = "scooter_type")
    private Set<String> allowedTypes;

    protected TripPolicy() { }

    public TripPolicy(RiderCategory category, LocalTime allowedFrom, LocalTime allowedUntil,
            String timeZone, int minimumBattery, int maxMinutes, BigDecimal overtimeRate, Set<String> allowedTypes) {
        if (category == null || allowedFrom == null || allowedUntil == null || timeZone == null
                || minimumBattery < 0 || minimumBattery > 100 || maxMinutes <= 0
                || overtimeRate == null || overtimeRate.signum() < 0 || allowedTypes == null
                || allowedTypes.isEmpty() || allowedTypes.stream().anyMatch(t -> t == null || t.isBlank())) {
            throw new IllegalArgumentException("Valid trip policy values are required");
        }
        ZoneId.of(timeZone);
        this.category = category;
        this.allowedFrom = allowedFrom;
        this.allowedUntil = allowedUntil;
        this.timeZone = timeZone;
        this.minimumBattery = minimumBattery;
        this.maxMinutes = maxMinutes;
        this.overtimeRate = overtimeRate;
        this.allowedTypes = allowedTypes.stream().map(t -> t.strip().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    public void requireAllowed(Instant now, String type, int battery) {
        if (!allowedTypes.contains(type.toLowerCase(Locale.ROOT))) {
            throw new ForbiddenException("This rider category cannot use scooter type " + type);
        }
        LocalTime time = now.atZone(zone()).toLocalTime();
        boolean allowed = allowedFrom.equals(allowedUntil)
                || (allowedFrom.isBefore(allowedUntil)
                    ? !time.isBefore(allowedFrom) && time.isBefore(allowedUntil)
                    : !time.isBefore(allowedFrom) || time.isBefore(allowedUntil));
        if (!allowed) {
            throw new ForbiddenException("Scooter use is allowed from " + allowedFrom + " until "
                    + allowedUntil + " (" + timeZone + ")");
        }
        if (battery < minimumBattery) {
            throw new ConflictException("Battery must be at least " + minimumBattery + "%");
        }
    }

    public ZoneId zone() { return ZoneId.of(timeZone); }
    public int getMaxMinutes() { return maxMinutes; }
    public BigDecimal getOvertimeRate() { return overtimeRate; }
}
