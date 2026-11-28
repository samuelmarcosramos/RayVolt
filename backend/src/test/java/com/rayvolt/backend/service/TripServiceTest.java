package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("PT-17")
class TripServiceTest {
    private final TripRepository trips = mock(TripRepository.class);
    private final ScooterRepository scooters = mock(ScooterRepository.class);
    private final ScooterStateRepository states = mock(ScooterStateRepository.class);
    private final HubRepository hubs = mock(HubRepository.class);
    private final ScooterLifecycleService lifecycle = mock(ScooterLifecycleService.class);
    private final CommandService commands = mock(CommandService.class);
    private final SseService sse = mock(SseService.class);
    private final Instant now = Instant.parse("2026-11-25T10:00:00Z");
    private final TripService service = new TripService(trips, scooters, states, hubs, lifecycle,
            commands, sse, Clock.fixed(now, ZoneOffset.UTC));
    private Trip trip;
    private Scooter scooter;

    @BeforeEach
    void setup() {
        trip = new Trip(7L, now.minusSeconds(600), 1L, 0, 30, BigDecimal.ONE);
        scooter = new Scooter(7L, "Standard", 1L, ScooterStatus.IN_USE);
        when(trips.findScooterIdById(3L)).thenReturn(Optional.of(7L));
        when(trips.findByIdForUpdate(3L)).thenReturn(Optional.of(trip));
        when(scooters.findByIdForUpdate(7L)).thenReturn(Optional.of(scooter));
        when(trips.save(trip)).thenReturn(trip);
        when(hubs.existsById(2L)).thenReturn(true);
    }

    private ScooterState telemetry(Long hub, PositionStatus position, boolean fault) {
        ScooterState state = new ScooterState(new TelemetryReading(7L, now, 80, false,
                0, hub, 40, -8, position, fault), now);
        when(states.findById(7L)).thenReturn(Optional.of(state));
        return state;
    }

    @Test
    void returnsToAnotherHubAndRequestsLock() {
        ScooterState state = telemetry(2L, PositionStatus.AT_HUB, false);
        assertThat(service.end(3L)).isSameAs(trip);
        assertThat(scooter.getHubId()).isEqualTo(2L);
        verify(lifecycle).changeStatus(7L, ScooterStatus.AVAILABLE, StateChangeCause.USER);
        verify(commands).send(7L, ScooterCommand.LOCK);
        verify(sse).publish(scooter, state);
    }

    @Test
    void reportedDamageRequiresMaintenance() {
        telemetry(2L, PositionStatus.AT_HUB, true);
        service.end(3L);
        verify(lifecycle).changeStatus(7L, ScooterStatus.MAINTENANCE, StateChangeCause.USER);
    }

    @Test
    void endsOutsideHubEvenWhenTelemetryStillContainsLastHub() {
        telemetry(1L, PositionStatus.OUTSIDE_HUB, false);
        assertThat(service.end(3L).getNoHubCharge()).isEqualByComparingTo("5.00");
        assertThat(trip.getEndHubId()).isNull();
        verify(commands).send(7L, ScooterCommand.LOCK);
    }

    @Test
    void missingTelemetryDoesNotEndTripOrSendCommands() {
        assertThatThrownBy(() -> service.end(3L)).isInstanceOf(ConflictException.class);
        assertThat(trip.getEndedAt()).isNull();
        verifyNoInteractions(lifecycle, commands, sse);
    }

    @Test
    void missingTripReturnsNotFound() {
        assertThatThrownBy(() -> service.end(99L)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(scooters, commands);
    }

    @Test
    void duplicateEndCannotChargeOrLockAgain() {
        telemetry(2L, PositionStatus.AT_HUB, false);
        service.end(3L);
        assertThatThrownBy(() -> service.end(3L)).isInstanceOf(ConflictException.class);
        verify(commands, times(1)).send(7L, ScooterCommand.LOCK);
        verify(trips, times(1)).save(trip);
    }


    @Test
    void nonRidingScooterCannotEndAnActiveTrip() {
        scooter.changeStatus(ScooterStatus.AVAILABLE);
        assertThatThrownBy(() -> service.end(3L)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(commands, lifecycle);
    }

    @Test
    void hubPositionRequiresAHubId() {
        telemetry(null, PositionStatus.AT_HUB, false);
        assertThatThrownBy(() -> service.end(3L)).isInstanceOf(ConflictException.class);
        assertThat(trip.getEndedAt()).isNull();
        verifyNoInteractions(commands);
    }

    @Test
    void unknownHubAndPreTripTelemetryAreRejected() {
        telemetry(99L, PositionStatus.AT_HUB, false);
        assertThatThrownBy(() -> service.end(3L)).isInstanceOf(ConflictException.class);
        when(states.findById(7L)).thenReturn(Optional.of(new ScooterState(new TelemetryReading(
                7L, now.minusSeconds(601), 80, false, 0, 2L, 40, -8, PositionStatus.AT_HUB, false), now)));
        assertThatThrownBy(() -> service.end(3L)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(commands);
    }
}
