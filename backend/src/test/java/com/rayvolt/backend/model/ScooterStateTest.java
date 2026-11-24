package com.rayvolt.backend.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-46")
class ScooterStateTest {
    private static final Instant START = Instant.parse("2026-11-24T10:00:00Z");
    private static final double KM_PER_MILLIDEGREE_LAT = 0.1111951;

    private TelemetryReading reading(int second, double lat, PositionStatus status) {
        return new TelemetryReading(7L, START.plusSeconds(second), 90, status == PositionStatus.AT_HUB,
                status == PositionStatus.MOVING ? 15 : 0, status == PositionStatus.AT_HUB ? 1L : null,
                lat, -8.7175, status, false);
    }

    @Test
    void firstReadingStartsTheOdometerAtZero() {
        ScooterState state = new ScooterState(reading(0, 40.612, PositionStatus.AT_HUB), START.plusMillis(200));
        assertThat(state.getOdometerKm()).isZero();
        assertThat(state.getReceivedAt()).isEqualTo(START.plusMillis(200));
        assertThat(state.getHubId()).isEqualTo(1L);
    }

    @Test
    void wholeRideIsCountedIncludingTheStretchIntoTheHub() {
        ScooterState state = new ScooterState(reading(0, 40.612, PositionStatus.MOVING), START);
        state.update(reading(5, 40.613, PositionStatus.MOVING), START);
        state.update(reading(10, 40.614, PositionStatus.AT_HUB), START);
        assertThat(state.getOdometerKm()).as("Two stretches of 0.001 degrees, the last one arriving at the hub")
                .isCloseTo(2 * KM_PER_MILLIDEGREE_LAT, within(0.0001));
        assertThat(state.getPositionStatus()).isEqualTo(PositionStatus.AT_HUB);
    }

    @Test
    void parkedScooterDoesNotAddDistance() {
        ScooterState state = new ScooterState(reading(0, 40.612, PositionStatus.AT_HUB), START);
        state.update(reading(5, 40.613, PositionStatus.AT_HUB), START);
        state.update(reading(10, 40.614, PositionStatus.OUTSIDE_HUB), START);
        state.update(reading(15, 40.615, PositionStatus.MOVING), START);
        assertThat(state.getOdometerKm()).as("Only stretches that start MOVING count").isZero();
    }

    @Test
    void onlyLaterReadingsAreNewer() {
        ScooterState state = new ScooterState(reading(5, 40.612, PositionStatus.AT_HUB), START);
        assertThat(state.isNewer(reading(6, 40.612, PositionStatus.AT_HUB))).isTrue();
        assertThat(state.isNewer(reading(5, 40.612, PositionStatus.AT_HUB))).as("Repeated").isFalse();
        assertThat(state.isNewer(reading(4, 40.612, PositionStatus.AT_HUB))).as("Out of order").isFalse();
    }

    @Test
    void distanceBetweenTwoValdrenHubs() {
        assertThat(ScooterState.distanceKm(40.6120, -8.7175, 40.6010, -8.7140)).isCloseTo(1.258, within(0.01));
    }
}
