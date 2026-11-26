package com.rayvolt.backend.service;

import com.rayvolt.backend.model.PositionStatus;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterLive;
import com.rayvolt.backend.model.ScooterState;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.model.TelemetryReading;
import com.rayvolt.backend.repository.ScooterRepository;
import com.rayvolt.backend.repository.ScooterStateRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterStateHistoryRepository;
import com.rayvolt.backend.model.Hub;
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
    private final SseService sse = mock(SseService.class);
    private final HubRepository hubs = mock(HubRepository.class);
    private final ScooterStateHistoryRepository history = mock(ScooterStateHistoryRepository.class);
    private final ScooterLifecycleService lifecycle = new ScooterLifecycleService(scooters, hubs, history,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final TelemetryService service = new TelemetryService(scooters, states, Clock.fixed(NOW, ZoneOffset.UTC),
            sse, lifecycle);

    @BeforeEach
    void prepareLifecycle() {
        when(hubs.findByIdForUpdate(1L)).thenReturn(Optional.of(new Hub(1L, "Synthetic hub", 2, null, null)));
        when(scooters.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private TelemetryReading reading(int second, double lat, PositionStatus status) {
        return new TelemetryReading(7L, Instant.parse("2026-11-24T10:00:00Z").plusSeconds(second), 80, false, 15,
                null, lat, -8.7175, status, false);
    }

    private TelemetryReading faultReading() {
        return new TelemetryReading(7L, Instant.parse("2026-11-24T10:00:00Z"), 80, true, 0,
                1L, 40.612, -8.7175, PositionStatus.AT_HUB, true);
    }

    private Scooter scooterIs(ScooterStatus status) {
        Scooter scooter = new Scooter(7L, "Standard", 1L, status);
        when(scooters.findByIdForUpdate(7L)).thenReturn(Optional.of(scooter));
        return scooter;
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
        when(scooters.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        service.record(reading(0, 40.612, PositionStatus.AT_HUB));
        verifyNoInteractions(states);
    }

    @Test
    void retiredScooterIsIgnored() {
        scooterIs(ScooterStatus.RETIRED);
        service.record(reading(0, 40.612, PositionStatus.AT_HUB));
        verifyNoInteractions(states);
    }

    @Test
    void readingWithoutFaultDoesNotTouchTheScooterStatus() {
        scooterIs(ScooterStatus.AVAILABLE);
        when(states.findById(7L)).thenReturn(Optional.empty());
        service.record(reading(0, 40.612, PositionStatus.AT_HUB));
        verify(scooters, never()).save(any());
    }

    @Test
    void readingLocksTheScooterBeforeCheckingItsStatus() {
        scooterIs(ScooterStatus.AVAILABLE);
        when(states.findById(7L)).thenReturn(Optional.empty());
        service.record(faultReading());
        verify(scooters, times(2)).findByIdForUpdate(7L);
        verify(scooters, never()).findById(any());
    }

    @Test
    void faultInTelemetryMovesAnAvailableScooterToMaintenance() {
        Scooter scooter = scooterIs(ScooterStatus.AVAILABLE);
        when(states.findById(7L)).thenReturn(Optional.empty());
        service.record(faultReading());
        assertThat(scooter.getStatus()).isEqualTo(ScooterStatus.MAINTENANCE);
        verify(scooters).save(scooter);
    }

    @Test
    void faultInTelemetryDuringARideKeepsTheScooterInUseAndStoresTheFault() {
        Scooter scooter = scooterIs(ScooterStatus.IN_USE);
        when(states.findById(7L)).thenReturn(Optional.empty());
        service.record(faultReading());
        assertThat(scooter.getStatus()).isEqualTo(ScooterStatus.IN_USE);
        verify(scooters, never()).save(any());
        ArgumentCaptor<ScooterState> saved = ArgumentCaptor.forClass(ScooterState.class);
        verify(states).save(saved.capture());
        assertThat(saved.getValue().isFault()).isTrue();
    }

    @Test
    void faultEventMovesAReservedScooterToMaintenance() {
        Scooter scooter = scooterIs(ScooterStatus.RESERVED);
        service.recordFault(7L);
        assertThat(scooter.getStatus()).isEqualTo(ScooterStatus.MAINTENANCE);
        verify(scooters).save(scooter);
    }

    @Test
    void repeatedFaultOnAScooterInMaintenanceSavesNothing() {
        scooterIs(ScooterStatus.MAINTENANCE);
        service.recordFault(7L);
        verify(scooters, never()).save(any());
    }

    @Test
    void faultForUnknownOrRetiredScooterIsIgnored() {
        when(scooters.findByIdForUpdate(8L)).thenReturn(Optional.empty());
        service.recordFault(8L);
        Scooter retired = scooterIs(ScooterStatus.RETIRED);
        service.recordFault(7L);
        assertThat(retired.getStatus()).isEqualTo(ScooterStatus.RETIRED);
        verify(scooters, never()).save(any());
    }

    @Test
    void savedReadingIsSentToTheLiveDashboard() {
        scooterIs(ScooterStatus.AVAILABLE);
        when(states.findById(7L)).thenReturn(Optional.empty());
        service.record(reading(0, 40.612, PositionStatus.AT_HUB));
        verify(sse).publish(new ScooterLive(7L, ScooterStatus.AVAILABLE, 80, 40.612, -8.7175, PositionStatus.AT_HUB,
                false, NOW));
    }

    @Test
    void faultInTelemetryIsSentWithTheNewStatus() {
        scooterIs(ScooterStatus.AVAILABLE);
        when(states.findById(7L)).thenReturn(Optional.empty());
        service.record(faultReading());
        ArgumentCaptor<ScooterLive> sent = ArgumentCaptor.forClass(ScooterLive.class);
        verify(sse).publish(sent.capture());
        assertThat(sent.getValue().status()).isEqualTo(ScooterStatus.MAINTENANCE);
        assertThat(sent.getValue().fault()).isTrue();
    }

    @Test
    void ignoredReadingsAreNotSent() {
        when(scooters.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        service.record(reading(0, 40.612, PositionStatus.AT_HUB));
        verifyNoInteractions(sse);
    }

    @Test
    void faultEventIsSentOnlyWhenSomethingChanges() {
        scooterIs(ScooterStatus.AVAILABLE);
        when(states.findById(7L)).thenReturn(Optional.empty());
        service.recordFault(7L);
        service.recordFault(7L);
        ArgumentCaptor<ScooterLive> sent = ArgumentCaptor.forClass(ScooterLive.class);
        verify(sse, times(1)).publish(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new ScooterLive(7L, ScooterStatus.MAINTENANCE, null, null, null, null,
                false, null));
    }

    @Test
    void faultEventDuringARideKeepsTheScooterInUseAndStoresTheFault() {
        Scooter scooter = scooterIs(ScooterStatus.IN_USE);
        ScooterState state = new ScooterState(reading(0, 40.612, PositionStatus.MOVING), NOW);
        when(states.findById(7L)).thenReturn(Optional.of(state));
        service.recordFault(7L);
        assertThat(scooter.getStatus()).isEqualTo(ScooterStatus.IN_USE);
        verify(scooters, never()).save(any());
        verify(states).save(state);
        assertThat(state.isFault()).isTrue();
    }

    @Test
    void faultEventDuringARideIsSentToTheLiveDashboard() {
        scooterIs(ScooterStatus.IN_USE);
        ScooterState state = new ScooterState(reading(0, 40.612, PositionStatus.MOVING), NOW);
        when(states.findById(7L)).thenReturn(Optional.of(state));
        service.recordFault(7L);
        service.recordFault(7L);
        ArgumentCaptor<ScooterLive> sent = ArgumentCaptor.forClass(ScooterLive.class);
        verify(sse, times(1)).publish(sent.capture());
        assertThat(sent.getValue().status()).isEqualTo(ScooterStatus.IN_USE);
        assertThat(sent.getValue().fault()).isTrue();
    }
}
