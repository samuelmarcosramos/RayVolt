package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.BadRequestException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-33")
class HubDetailsTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0})
    void capacityMustBePositive(Integer capacity) {
        assertThatThrownBy(() -> new HubDetails("Gate", "Security", capacity, 0.0, 0.0))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @CsvSource({"-90,-180", "90,180", "0,0"})
    void coordinateBoundariesAreValid(double lat, double lon) {
        assertThat(new HubDetails("Gate", "Security", 1, lat, lon).lat()).isEqualTo(lat);
    }

    @ParameterizedTest
    @CsvSource(value = {"-90.01,0", "90.01,0", "0,-180.01", "0,180.01", "NULL,0", "0,NULL", "NaN,0", "0,Infinity"}, nullValues = "NULL")
    void invalidCoordinatesAreRejected(Double lat, Double lon) {
        assertThatThrownBy(() -> new HubDetails("Gate", "Security", 1, lat, lon))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void requiredTextCannotBeBlank(String text) {
        assertThatThrownBy(() -> new HubDetails(text, "Security", 1, 0.0, 0.0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new HubDetails("Gate", text, 1, 0.0, 0.0)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void textLengthMatchesStorageLimits() {
        assertThat(new HubDetails("x".repeat(120), "y".repeat(120), 1, 0.0, 0.0).name()).hasSize(120);
        assertThatThrownBy(() -> new HubDetails("x".repeat(121), "Security", 1, 0.0, 0.0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new HubDetails("Gate", "y".repeat(121), 1, 0.0, 0.0)).isInstanceOf(BadRequestException.class);
    }
}
