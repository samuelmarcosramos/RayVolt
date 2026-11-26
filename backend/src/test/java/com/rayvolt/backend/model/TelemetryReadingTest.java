package com.rayvolt.backend.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-46")
class TelemetryReadingTest {
    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    void validReadingIsAccepted() {
        assertThatCode(() -> new TelemetryReading(7L, NOW, 0, true, 0, null, -90, 180, PositionStatus.OUTSIDE_HUB, true))
                .doesNotThrowAnyException();
    }

    @Test
    void missingOrOutOfRangeValuesAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TelemetryReading(null, NOW, 50, true, 0, 1L, 40, -8, PositionStatus.AT_HUB, false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TelemetryReading(7L, null, 50, true, 0, 1L, 40, -8, PositionStatus.AT_HUB, false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TelemetryReading(7L, NOW, 50, true, 0, 1L, 40, -8, null, false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TelemetryReading(7L, NOW, 101, true, 0, 1L, 40, -8, PositionStatus.AT_HUB, false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TelemetryReading(7L, NOW, -1, true, 0, 1L, 40, -8, PositionStatus.AT_HUB, false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TelemetryReading(7L, NOW, 50, false, -1, null, 40, -8, PositionStatus.MOVING, false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TelemetryReading(7L, NOW, 50, true, 0, 1L, 91, -8, PositionStatus.AT_HUB, false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TelemetryReading(7L, NOW, 50, true, 0, 1L, 40, -181, PositionStatus.AT_HUB, false));
    }
}
