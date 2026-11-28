package com.rayvolt.backend.billing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "deposit_holds")
public class DepositHold {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long riderId;
    private Long tripId;
    private BigDecimal amount;
    private Instant heldAt;

    protected DepositHold() { }

    DepositHold(Long riderId, BigDecimal amount, Instant heldAt) {
        this.riderId = riderId;
        this.amount = amount;
        this.heldAt = heldAt;
    }

    public void attachTo(Long tripId) {
        if (tripId == null || tripId <= 0 || this.tripId != null) {
            throw new IllegalArgumentException("A hold can be attached to one persisted trip");
        }
        this.tripId = tripId;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getHeldAt() { return heldAt; }
}
