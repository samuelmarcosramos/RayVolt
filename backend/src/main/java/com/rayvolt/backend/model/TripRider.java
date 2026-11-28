package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.ForbiddenException;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "trip_riders")
public class TripRider {
    @Id private Long id;
    @Enumerated(EnumType.STRING) private RiderCategory category;
    private boolean enabled;
    private LocalDate validUntil;

    protected TripRider() { }

    public TripRider(Long id, RiderCategory category, boolean enabled, LocalDate validUntil) {
        if (id == null || id <= 0 || category == null
                || (category.requiresDeposit() && validUntil == null)) {
            throw new IllegalArgumentException("Rider identity, category and external account end date are required");
        }
        this.id = id;
        this.category = category;
        this.enabled = enabled;
        this.validUntil = validUntil;
    }

    public void requireActive(LocalDate today) {
        if (!enabled || (validUntil != null && today.isAfter(validUntil))) {
            throw new ForbiddenException("Rider account is disabled or expired");
        }
    }

    public Long getId() { return id; }
    public RiderCategory getCategory() { return category; }
}
