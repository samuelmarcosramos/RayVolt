package com.rayvolt.backend.model;

public enum RiderCategory {
    WORKER, SECURITY_GUARD, CONTRACTOR, VISITOR;

    public boolean requiresDeposit() {
        return this == CONTRACTOR || this == VISITOR;
    }
}
