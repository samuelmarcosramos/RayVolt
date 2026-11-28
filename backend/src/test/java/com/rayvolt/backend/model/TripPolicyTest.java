package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-16")
class TripPolicyTest {
    private TripPolicy policy(String from, String until) {
        return new TripPolicy(RiderCategory.WORKER, LocalTime.parse(from), LocalTime.parse(until),
                "Europe/Lisbon", 10, 30, new BigDecimal("0.50"), Set.of("Standard"));
    }

    @ParameterizedTest
    @CsvSource({"07:59:59,false", "08:00:00,true", "08:00:01,true", "17:59:59,true", "18:00:00,false", "18:00:01,false"})
    void openingIsInclusiveAndClosingIsExclusive(String time, boolean allowed) {
        Instant now = LocalDate.parse("2026-11-25").atTime(LocalTime.parse(time)).toInstant(ZoneOffset.UTC);
        if (allowed) {
            assertThatCode(() -> policy("08:00", "18:00").requireAllowed(now, "Standard", 10)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policy("08:00", "18:00").requireAllowed(now, "Standard", 10))
                    .isInstanceOf(ForbiddenException.class).hasMessageContaining("08:00 until 18:00");
        }
    }

    @ParameterizedTest
    @CsvSource({"9,false", "10,true", "11,true"})
    void minimumBatteryIncludesTheExactThreshold(int battery, boolean allowed) {
        Instant now = Instant.parse("2026-11-25T10:00:00Z");
        if (allowed) {
            assertThatCode(() -> policy("08:00", "18:00").requireAllowed(now, "Standard", battery)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policy("08:00", "18:00").requireAllowed(now, "Standard", battery))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("at least 10%");
        }
    }

    @Test
    void supportsNightShiftsAndFullDayPolicies() {
        for (String time : Set.of("2026-11-25T22:00:00Z", "2026-11-26T05:59:59Z")) {
            assertThatCode(() -> policy("22:00", "06:00").requireAllowed(Instant.parse(time), "Standard", 80))
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> policy("22:00", "06:00").requireAllowed(
                Instant.parse("2026-11-26T06:00:00Z"), "Standard", 80)).isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> policy("00:00", "00:00").requireAllowed(
                Instant.parse("2026-11-25T12:00:00Z"), "Standard", 80)).doesNotThrowAnyException();
    }

    @Test
    void usesConfiguredLocalTimeDuringDaylightSaving() {
        assertThatCode(() -> policy("08:00", "18:00").requireAllowed(
                Instant.parse("2026-07-01T07:00:00Z"), "Standard", 80)).doesNotThrowAnyException();
    }

    @Test
    void forbiddenTypesReturnAnAccessRestriction() {
        assertThatThrownBy(() -> policy("00:00", "00:00").requireAllowed(Instant.EPOCH, "Patrol", 80))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsInvalidPolicyValues() {
        assertThatThrownBy(() -> new TripPolicy(RiderCategory.WORKER, LocalTime.MIN, LocalTime.MIN,
                "Europe/Lisbon", 101, 30, BigDecimal.ONE, Set.of("Standard")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
