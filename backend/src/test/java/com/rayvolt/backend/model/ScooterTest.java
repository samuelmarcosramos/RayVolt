package com.rayvolt.backend.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import com.rayvolt.backend.errors.BadRequestException;
import com.rayvolt.backend.errors.ConflictException;

@Tag("PT-46")
class ScooterTest {

    @Test
    @Tag("PT-14")
    void batteryPercentageDefaultsToOneHundredAndRejectsOutOfRangeValues() {
        assertThat(new Scooter("Standard", 1L).getBatteryPercent()).isEqualTo(100);
        assertThat(new Scooter(7L, "Standard", 1L, ScooterStatus.AVAILABLE, 0).getBatteryPercent()).isZero();
        assertThat(new Scooter(8L, "Standard", 1L, ScooterStatus.AVAILABLE, 100).getBatteryPercent())
                .isEqualTo(100);
        assertThatThrownBy(() -> new Scooter(9L, "Standard", 1L, ScooterStatus.AVAILABLE, -1))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new Scooter(10L, "Standard", 1L, ScooterStatus.AVAILABLE, 101))
                .isInstanceOf(BadRequestException.class);
    }

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
