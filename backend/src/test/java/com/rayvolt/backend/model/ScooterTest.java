package com.rayvolt.backend.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("PT-46")
class ScooterTest {

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, MAINTENANCE",
            "RESERVED, MAINTENANCE",
            "IN_USE, IN_USE",
            "MAINTENANCE, MAINTENANCE",
            "RETIRED, RETIRED"
    })
    void faultMovesOnlyAvailableAndReservedScootersToMaintenance(ScooterStatus before, ScooterStatus after) {
        Scooter scooter = new Scooter(7L, "Standard", 1L, before);
        scooter.markFaulty();
        assertThat(scooter.getStatus()).isEqualTo(after);
    }
}
