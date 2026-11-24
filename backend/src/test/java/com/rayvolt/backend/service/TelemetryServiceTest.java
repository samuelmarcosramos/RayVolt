package com.rayvolt.backend.service;

import com.rayvolt.backend.model.PositionStatus;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterState;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.model.TelemetryReading;
import com.rayvolt.backend.repository.ScooterRepository;
import com.rayvolt.backend.repository.ScooterStateRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("PT-46")
class TelemetryServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-24T10:00:01Z");
    private final ScooterRepository scooters = mock(ScooterRepository.class);
    private final ScooterStateRepository states = mock(ScooterStateRepository.class);
    private final TelemetryService service = new TelemetryService(scooters, states, Clock.fixed(NOW, ZoneOffset.UTC));

    private TelemetryReading reading(int second, double lat, PositionStatus status) {
        return new TelemetryReading(7L, Instant.parse("2026-11-24T10:00:00Z").plusSeconds(second), 80, false, 15,
                null, lat, -8.7175, status, false);
    }

    private void scooterIs(ScooterStatus status) {
        when(scooters.findById(7L)).thenReturn(Optional.of(new Scooter(7L, "Standard", 1L, status)));
    }

    @Test
    void firstReadingCreatesTheStateWithTheReceivedTime() {
        scooterIs(ScooterStatus.AVAILABLE);
        when(states.findById(7L)).thenReturn(Optional.empty());
        service.record(reading(0, 40.612, PositionStatus.MOVING));
        ArgumentCaptor<ScooterState> saved = ArgumentCaptor.forClass(ScooterState.class);
        verify(states).save(saved.capture());
        assertThat(saved.getValue().getScooterId()).isEqualTo(7L);
        assertThat(saved.getValue().getReceivedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getBattery()).isEqualTo(80);
    }

    @Test
    void newerReadingUpdatesTheStoredState() {
        scooterIs(ScooterStatus.IN_USE);
        ScooterState state = new ScooterState(reading(0, 40.612, PositionStatus.MOVING), NOW);
        when(states.findById(7L)).thenReturn(Optional.of(state));
        service.record(reading(5, 40.613, PositionStatus.MOVING));
        verify(states).save(state);
        assertThat(state.getLat()).isEqualTo(40.613);
        assertThat(state.getOdometerKm()).isPositive();
    }

    @Test
    void oldOrRepeatedReadingsAreIgnored() {
        scooterIs(ScooterStatus.IN_USE);
        ScooterState state = new ScooterState(reading(5, 40.613, PositionStatus.MOVING), NOW);
        when(states.findById(7L)).thenReturn(Optional.of(state));
        service.record(reading(5, 40.614, PositionStatus.MOVING));
        service.record(reading(4, 40.611, PositionStatus.MOVING));
        verify(states, never()).save(any());
        assertThat(state.getLat()).isEqualTo(40.613);
    }

    @Test
    void unknownScooterIsIgnored() {
        when(scooters.findById(7L)).thenReturn(Optional.empty());
        service.record(reading(0, 40.612, PositionStatus.AT_HUB));
        verifyNoInteractions(states);
    }

    @Test
    void retiredScooterIsIgnored() {
        scooterIs(ScooterStatus.RETIRED);
        service.record(reading(0, 40.612, PositionStatus.AT_HUB));
        verifyNoInteractions(states);
    }
}
