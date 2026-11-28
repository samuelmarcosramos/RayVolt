package com.rayvolt.backend.service;

import com.rayvolt.backend.billing.*;
import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("PT-16")
class TripStartServiceTest {
    private final TripRepository trips = mock(TripRepository.class);
    private final TripRiderRepository riders = mock(TripRiderRepository.class);
    private final TripPolicyRepository policies = mock(TripPolicyRepository.class);
    private final TripBookingRepository bookings = mock(TripBookingRepository.class);
    private final ScooterRepository scooters = mock(ScooterRepository.class);
    private final ScooterStateRepository states = mock(ScooterStateRepository.class);
    private final ScooterLifecycleService lifecycle = mock(ScooterLifecycleService.class);
    private final CommandService commands = mock(CommandService.class);
    private final DepositService deposits = mock(DepositService.class);
    private final SseService sse = mock(SseService.class);
    private final Instant now = Instant.parse("2026-11-25T10:00:00Z");
    private final TripStartService service = new TripStartService(trips, riders, policies, bookings, scooters,
            states, lifecycle, commands, deposits, sse, Clock.fixed(now, ZoneOffset.UTC));
    private Scooter scooter;

    @BeforeEach
    void setup() {
        rider(RiderCategory.WORKER);
        scooter = new Scooter(7L, "Standard", 1L, ScooterStatus.AVAILABLE);
        when(scooters.findByIdForUpdate(7L)).thenReturn(Optional.of(scooter));
        when(bookings.findByRiderIdAndExpiresAtAfter(1L, now)).thenReturn(List.of());
        when(trips.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        telemetry(PositionStatus.AT_HUB, true, false);
    }

    private void rider(RiderCategory category) {
        when(riders.findByIdForUpdate(1L)).thenReturn(Optional.of(new TripRider(1L, category, true,
                category.requiresDeposit() ? LocalDate.of(2026, 12, 1) : null)));
        when(policies.findById(category)).thenReturn(Optional.of(new TripPolicy(category, LocalTime.MIN,
                LocalTime.MIN, "Europe/Lisbon", 10, 30, new BigDecimal("0.50"), Set.of("Standard"))));
    }

    private void telemetry(PositionStatus position, boolean locked, boolean fault) {
        when(states.findById(7L)).thenReturn(Optional.of(new ScooterState(new TelemetryReading(
                7L, now, 10, locked, 0, 1L, 40, -8, position, fault), now)));
    }

    @Test
    void startsAvailableScooterAtHubAndCapturesPolicy() {
        Trip trip = service.start(1L, 7L);
        assertThat(trip.getRiderId()).isEqualTo(1L);
        assertThat(trip.getStartedAt()).isEqualTo(now);
        assertThat(trip.getStartHubId()).isEqualTo(1L);
        verify(lifecycle).changeStatus(7L, ScooterStatus.IN_USE, StateChangeCause.USER);
        verify(commands).send(7L, ScooterCommand.UNLOCK);
        verifyNoInteractions(deposits);
    }

    @Test
    void consumesTheRidersUnexpiredReservation() {
        scooter.changeStatus(ScooterStatus.RESERVED);
        TripBooking booking = new TripBooking(7L, 1L, now.plusSeconds(1));
        when(bookings.findById(7L)).thenReturn(Optional.of(booking));
        service.start(1L, 7L);
        verify(bookings).delete(booking);
    }

    @Test
    void anotherRidersReservationCannotBeStarted() {
        scooter.changeStatus(ScooterStatus.RESERVED);
        when(bookings.findById(7L)).thenReturn(Optional.of(new TripBooking(7L, 2L, now.plusSeconds(600))));
        assertThatThrownBy(() -> service.start(1L, 7L)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(deposits, commands, lifecycle);
    }

    @Test
    void failedDepositDoesNotSaveTripOrUnlock() {
        rider(RiderCategory.CONTRACTOR);
        when(deposits.hold(1L)).thenThrow(new ConflictException("Deposit hold failed"));
        assertThatThrownBy(() -> service.start(1L, 7L)).isInstanceOf(ConflictException.class);
        verify(trips, never()).save(any());
        verifyNoInteractions(commands, lifecycle);
    }

    @Test
    void visitorDepositIsHeldBeforeTripPersistenceAndUnlock() {
        rider(RiderCategory.VISITOR);
        service.start(1L, 7L);
        var order = inOrder(deposits, trips, lifecycle, commands);
        order.verify(deposits).hold(1L);
        order.verify(trips).save(any());
        order.verify(lifecycle).changeStatus(7L, ScooterStatus.IN_USE, StateChangeCause.USER);
        order.verify(commands).send(7L, ScooterCommand.UNLOCK);
    }

    @Test
    void existingTripOrOtherBookingBlocksStart() {
        when(trips.existsByRiderIdAndEndedAtIsNull(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.start(1L, 7L)).isInstanceOf(ConflictException.class);
        when(trips.existsByRiderIdAndEndedAtIsNull(1L)).thenReturn(false);
        when(bookings.findByRiderIdAndExpiresAtAfter(1L, now))
                .thenReturn(List.of(new TripBooking(8L, 1L, now.plusSeconds(600))));
        assertThatThrownBy(() -> service.start(1L, 7L)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(commands, deposits);
    }

    @Test
    void missingFaultyUnlockedOrOutsideTelemetryBlocksStart() {
        when(states.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.start(1L, 7L)).isInstanceOf(ConflictException.class);
        telemetry(PositionStatus.AT_HUB, false, false);
        assertThatThrownBy(() -> service.start(1L, 7L)).isInstanceOf(ConflictException.class);
        telemetry(PositionStatus.AT_HUB, true, true);
        assertThatThrownBy(() -> service.start(1L, 7L)).isInstanceOf(ConflictException.class);
        telemetry(PositionStatus.OUTSIDE_HUB, true, false);
        assertThatThrownBy(() -> service.start(1L, 7L)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(commands, deposits);
    }

    @Test
    void unknownScooterAndMissingPolicyAreRejected() {
        when(scooters.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.start(1L, 7L)).isInstanceOf(NotFoundException.class);
        when(policies.findById(RiderCategory.WORKER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.start(1L, 7L)).isInstanceOf(ForbiddenException.class);
    }
}
