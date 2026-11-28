package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.ConflictException;
import org.junit.jupiter.api.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-16")
@Tag("PT-15")
class TripBookingTest {
    @Test
    void aNewBookingExpiresExactlyTenMinutesAfterCreation() {
        Instant madeAt = Instant.parse("2026-11-25T10:00:00Z");
        TripBooking booking = TripBooking.reserve(7L, 1L, madeAt);
        assertThat(booking.getRiderId()).isEqualTo(1L);
        assertThat(booking.getExpiresAt()).isEqualTo(madeAt.plusSeconds(600));
        assertThat(booking.isExpiredAt(madeAt.plusSeconds(600).minusNanos(1)))
                .as("Booking remains active just before ten minutes").isFalse();
        assertThat(booking.isExpiredAt(madeAt.plusSeconds(600)))
                .as("Booking expires exactly at ten minutes").isTrue();
        assertThat(booking.isExpiredAt(madeAt.plusSeconds(600).plusNanos(1)))
                .as("Booking stays expired after ten minutes").isTrue();
    }

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
