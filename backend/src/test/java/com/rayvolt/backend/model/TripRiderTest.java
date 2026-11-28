package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.ForbiddenException;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-16")
class TripRiderTest {
    private final LocalDate today = LocalDate.of(2026, 11, 25);

    @Test
    void externalAccountCanRideThroughItsEndDateOnly() {
        TripRider rider = new TripRider(1L, RiderCategory.VISITOR, true, today);
        assertThatCode(() -> rider.requireActive(today.minusDays(1))).doesNotThrowAnyException();
        assertThatCode(() -> rider.requireActive(today)).doesNotThrowAnyException();
        assertThatThrownBy(() -> rider.requireActive(today.plusDays(1))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void disabledAccountsCannotRide() {
        assertThatThrownBy(() -> new TripRider(1L, RiderCategory.WORKER, false, null).requireActive(today))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void externalAccountRequiresAnEndDate() {
        assertThatThrownBy(() -> new TripRider(1L, RiderCategory.CONTRACTOR, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
