package com.rayvolt.backend.model;

import java.time.Instant;

public record CommandReply(Long scooterId, Instant timestamp, String commandId, ScooterCommand command,
        Status status, String reason) {

    public CommandReply {
        if (scooterId == null || commandId == null || command == null || status == null) {
            throw new IllegalArgumentException("scooterId, commandId, command and status are required");
        }
    }

    public enum Status {
        COMPLETED, REJECTED
    }

    public boolean rejected() {
        return status == Status.REJECTED;
    }
}
