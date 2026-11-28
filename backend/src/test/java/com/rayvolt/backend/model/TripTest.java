package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.ConflictException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-17")
class TripTest {
    private static final Instant START = Instant.parse("2026-11-25T09:00:00Z");

    static Trip trip() { return new Trip(7L, START, 1L, 0, 30, new BigDecimal("0.50")); }
    static ScooterState state() {
        ScooterState state = new ScooterState(new TelemetryReading(7L, START, 90, false,
                10, null, 40, -8, PositionStatus.MOVING, false), START);
        state.update(new TelemetryReading(7L, START.plusSeconds(60), 89, false,
                0, 2L, 40.001, -8, PositionStatus.AT_HUB, false), START.plusSeconds(60));
        return state;
    }

    @ParameterizedTest
    @CsvSource({"2099,0", "2100,0", "2101,1", "2160,1", "2161,2"})
    void chargesOnlyStartedMinutesAfterTheFiveMinuteGrace(long seconds, long minutes) {
        Trip trip = trip();
        trip.end(START.plusSeconds(seconds), state(), 2L);
        assertThat(trip.getOvertimeMinutes()).as("Started minutes after grace").isEqualTo(minutes);
        assertThat(trip.getOvertimeCharge()).isEqualByComparingTo(new BigDecimal("0.50").multiply(BigDecimal.valueOf(minutes)));
    }

    @Test
    void subsecondOvertimeIsAStartedMinute() {
        Trip trip = trip();
        trip.end(START.plusSeconds(2100).plusNanos(1), state(), 2L);
        assertThat(trip.getOvertimeMinutes()).isEqualTo(1);
    }

    @Test
    void savesEndTimePositionAndTelemetryDistanceAtAnotherHub() {
        Trip trip = trip();
        ScooterState state = state();
        trip.end(START.plusSeconds(60), state, 2L);
        assertThat(trip.getEndedAt()).isEqualTo(START.plusSeconds(60));
        assertThat(trip.getEndHubId()).isEqualTo(2L);
        assertThat(trip.getEndLat()).isEqualTo(40.001);
        assertThat(trip.getEndLon()).isEqualTo(-8);
        assertThat(trip.getKmRidden()).isCloseTo(0.111195, within(0.000001));
        assertThat(trip.getNoHubCharge()).isEqualByComparingTo("0.00");
    }

    @Test
    void outsideHubChargeAndOvertimeAreCumulative() {
        Trip trip = trip();
        trip.end(START.plusSeconds(2101), state(), null);
        assertThat(trip.getEndHubId()).isNull();
        assertThat(trip.getNoHubCharge()).isEqualByComparingTo("5.00");
        assertThat(trip.getOvertimeCharge()).isEqualByComparingTo("0.50");
        assertThatThrownBy(() -> trip.end(START.plusSeconds(2200), state(), null))
                .isInstanceOf(ConflictException.class).hasMessage("Trip has already ended");
    }

    @Test
    void subtractsOdometerAtStartRatherThanChargingLifetimeDistance() {
        Trip trip = new Trip(7L, START, 1L, 0.1, 30, BigDecimal.ONE);
        trip.end(START.plusSeconds(60), state(), 2L);
        assertThat(trip.getKmRidden()).isCloseTo(0.011195, within(0.000001));
    }

    @Test
    void rejectsMissingPricing() {
        assertThatThrownBy(() -> new Trip(7L, START, 1L, 0, 30, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTimeBeforeStartAndDecreasingOdometer() {
        assertThatThrownBy(() -> trip().end(START.minusNanos(1), state(), 2L))
                .isInstanceOf(ConflictException.class);
        Trip trip = new Trip(7L, START, 1L, 10, 30, BigDecimal.ONE);
        assertThatThrownBy(() -> trip.end(START, state(), 2L)).isInstanceOf(ConflictException.class);
    }
}
