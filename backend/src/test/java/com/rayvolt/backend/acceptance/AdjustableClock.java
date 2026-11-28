package com.rayvolt.backend.acceptance;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

final class AdjustableClock extends Clock {
    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    AdjustableClock(Instant instant, ZoneId zone) { this(new AtomicReference<>(instant), zone); }
    private AdjustableClock(AtomicReference<Instant> instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    void set(Instant value) { instant.set(value); }
    @Override public Instant instant() { return instant.get(); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new AdjustableClock(instant, zone); }
}
