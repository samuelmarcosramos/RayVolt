package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.ConflictException;
import org.junit.jupiter.api.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-16")
class TripBookingTest {
    @Test
    void onlyTheOwnerCanUseTheBookingStrictlyBeforeExpiry() {
        Instant expiry = Instant.parse("2026-11-25T10:00:00Z");
        TripBooking booking = new TripBooking(7L, 1L, expiry);
        assertThatCode(() -> booking.requireOwnedBy(1L, expiry.minusNanos(1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> booking.requireOwnedBy(1L, expiry)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> booking.requireOwnedBy(1L, expiry.plusNanos(1))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> booking.requireOwnedBy(2L, expiry.minusSeconds(1))).isInstanceOf(ConflictException.class);
    }
}
