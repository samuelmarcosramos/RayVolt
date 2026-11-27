package com.rayvolt.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scooter_state_history")
public class ScooterStateChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long scooterId;
    @Enumerated(EnumType.STRING)
    private ScooterStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private ScooterStatus toStatus;
    private Instant changedAt;
    @Enumerated(EnumType.STRING)
    private StateChangeCause cause;

    protected ScooterStateChange() {
    }

    public ScooterStateChange(Long scooterId, ScooterStatus fromStatus, ScooterStatus toStatus,
            Instant changedAt, StateChangeCause cause) {
        this.scooterId = scooterId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
        this.cause = cause;
    }

    public Long getId() { return id; }
    public Long getScooterId() { return scooterId; }
    public ScooterStatus getFromStatus() { return fromStatus; }
    public ScooterStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
    public StateChangeCause getCause() { return cause; }
}
