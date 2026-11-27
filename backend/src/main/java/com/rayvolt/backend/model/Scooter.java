package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.BadRequestException;
import com.rayvolt.backend.errors.ConflictException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "scooters")
public class Scooter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String type;
    private Long hubId;
    @Enumerated(EnumType.STRING)
    private ScooterStatus status;
    protected Scooter() {
    }

    public Scooter(String type, Long hubId) {
        this(null, type, hubId, ScooterStatus.AVAILABLE);
    }

    public Scooter(Long id, String type, Long hubId, ScooterStatus status) {
        if (id != null && id <= 0) {
            throw new BadRequestException("Scooter and hub IDs must be positive numbers");
        }
        this.id = id;
        this.type = requireType(type);
        this.hubId = requireHub(hubId);
        this.status = Objects.requireNonNull(status, "Status is required");
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Long getHubId() {
        return hubId;
    }

    public ScooterStatus getStatus() {
        return status;
    }

    public void edit(String type, Long hubId) {
        requireEditable();
        this.type = requireType(type);
        this.hubId = requireHub(hubId);
    }

    public void retire() {
        changeStatus(ScooterStatus.RETIRED);
    }

    public void changeStatus(ScooterStatus target) {
        if (target == null) {
            throw new BadRequestException("Target state is required");
        }
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("Cannot change scooter state from " + status + " to " + target);
        }
        status = target;
    }

    public boolean visibleToUsers() {
        return status != ScooterStatus.RETIRED;
    }

    public void requireEditable() {
        if (status == ScooterStatus.IN_USE || status == ScooterStatus.RESERVED
                || status == ScooterStatus.RETIRED) {
            throw new ConflictException("An in-use, reserved or retired scooter cannot be changed");
        }
    }

    private static String requireType(String type) {
        if (type == null || type.isBlank()) {
            throw new BadRequestException("Scooter type is required");
        }
        return type.strip();
    }

    private static Long requireHub(Long hubId) {
        if (hubId == null || hubId <= 0) {
            throw new BadRequestException("Scooter and hub IDs must be positive numbers");
        }
        return hubId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Scooter scooter && id != null && id.equals(scooter.id);
    }

    @Override
    public int hashCode() {
        return Scooter.class.hashCode();
    }
}
