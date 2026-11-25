package com.rayvolt.backend.fleet.model;

import com.rayvolt.backend.common.exception.BadRequestException;
import com.rayvolt.backend.common.exception.ConflictException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-34")
class ScooterLifecycleTest {
    @ParameterizedTest(name = "{0} -> {1}: allowed={2}")
    @CsvSource({
        "AVAILABLE, AVAILABLE, false", "AVAILABLE, RESERVED, true", "AVAILABLE, IN_USE, true",
        "AVAILABLE, MAINTENANCE, true", "AVAILABLE, RETIRED, true",
        "RESERVED, AVAILABLE, true", "RESERVED, RESERVED, false", "RESERVED, IN_USE, true",
        "RESERVED, MAINTENANCE, true", "RESERVED, RETIRED, true",
        "IN_USE, AVAILABLE, true", "IN_USE, RESERVED, false", "IN_USE, IN_USE, false",
        "IN_USE, MAINTENANCE, true", "IN_USE, RETIRED, false",
        "MAINTENANCE, AVAILABLE, true", "MAINTENANCE, RESERVED, false", "MAINTENANCE, IN_USE, false",
        "MAINTENANCE, MAINTENANCE, false", "MAINTENANCE, RETIRED, true",
        "RETIRED, AVAILABLE, false", "RETIRED, RESERVED, false", "RETIRED, IN_USE, false",
        "RETIRED, MAINTENANCE, false", "RETIRED, RETIRED, false"
    })
    void enforcesEveryStatePair(ScooterStatus from, ScooterStatus to, boolean allowed) {
        Scooter original = new Scooter(7L, "Standard", 1L, from);
        if (allowed) {
            Scooter changed = original.changeStatus(to);
            assertThat(changed.status()).as("Allowed transition reaches its target").isEqualTo(to);
            assertThat(changed.id()).as("Transition keeps scooter identity").isEqualTo(original.id());
        } else {
            assertThatThrownBy(() -> original.changeStatus(to))
                    .as("Unlisted transitions, including self transitions, are rejected")
                    .isInstanceOf(ConflictException.class);
        }
        assertThat(original.status()).as("Original immutable scooter is unchanged").isEqualTo(from);
    }

    @Test
    void missingTargetIsInvalidInput() {
        Scooter scooter = new Scooter(7L, "Standard", 1L, ScooterStatus.AVAILABLE);
        assertThatThrownBy(() -> scooter.changeStatus(null)).isInstanceOf(BadRequestException.class);
    }
}
