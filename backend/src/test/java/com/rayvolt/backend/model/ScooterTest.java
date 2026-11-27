package com.rayvolt.backend.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import com.rayvolt.backend.errors.ConflictException;

@Tag("PT-46")
class ScooterTest {

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, MAINTENANCE",
            "RESERVED, MAINTENANCE",
            "IN_USE, MAINTENANCE",
            "MAINTENANCE, MAINTENANCE",
            "RETIRED, RETIRED"
    })
    void maintenanceTransitionsFollowTheLifecycle(ScooterStatus before, ScooterStatus after) {
        Scooter scooter = new Scooter(7L, "Standard", 1L, before);
        if (before == ScooterStatus.RETIRED || before == ScooterStatus.MAINTENANCE) {
            assertThatThrownBy(() -> scooter.changeStatus(ScooterStatus.MAINTENANCE))
                    .isInstanceOf(ConflictException.class);
        } else {
            scooter.changeStatus(ScooterStatus.MAINTENANCE);
        }
        assertThat(scooter.getStatus()).isEqualTo(after);
    }
}
