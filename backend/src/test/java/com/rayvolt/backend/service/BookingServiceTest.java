package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("PT-15")
class BookingServiceTest {
    private final TripBookingRepository bookings = mock(TripBookingRepository.class);
    private final TripRepository trips = mock(TripRepository.class);
    private final TripRiderRepository riders = mock(TripRiderRepository.class);
    private final TripPolicyRepository policies = mock(TripPolicyRepository.class);
    private final ScooterRepository scooters = mock(ScooterRepository.class);
    private final ScooterStateRepository states = mock(ScooterStateRepository.class);
    private final ScooterLifecycleService lifecycle = mock(ScooterLifecycleService.class);
    private final SseService sse = mock(SseService.class);
    private final Instant now = Instant.parse("2026-11-25T10:00:00Z");
    private final BookingService service = new BookingService(bookings, trips, riders, policies, scooters,
            states, lifecycle, sse, Clock.fixed(now, ZoneOffset.UTC));
    private Scooter scooter;

    @BeforeEach
    void setup() {
        scooter = new Scooter(7L, "Standard", 1L, ScooterStatus.AVAILABLE);
        when(scooters.findByIdForUpdate(7L)).thenReturn(Optional.of(scooter));
        when(riders.findByIdForUpdate(1L)).thenReturn(Optional.of(new TripRider(1L, RiderCategory.WORKER, true, null)));
        when(policies.findById(RiderCategory.WORKER)).thenReturn(Optional.of(new TripPolicy(
                RiderCategory.WORKER, LocalTime.MIN, LocalTime.MIN, "Europe/Lisbon", 10, 30,
                BigDecimal.ONE, Set.of("Standard"))));
        when(states.findById(7L)).thenReturn(Optional.of(new ScooterState(new TelemetryReading(7L, now,
                80, true, 0, 1L, 40, -8, PositionStatus.AT_HUB, false), now)));
        when(bookings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lifecycle.changeStatus(any(), any(), any())).thenAnswer(invocation -> {
            scooter.changeStatus(invocation.getArgument(1));
            return scooter;
        });
    }

    @Test
    void availableScooterIsReservedForExactlyTenMinutes() {
        TripBooking booking = service.book(1L, 7L);
        assertThat(booking.getScooterId()).isEqualTo(7L);
        assertThat(booking.getRiderId()).isEqualTo(1L);
        assertThat(booking.getExpiresAt()).isEqualTo(now.plusSeconds(600));
        assertThat(scooter.getStatus()).isEqualTo(ScooterStatus.RESERVED);
        verify(lifecycle).changeStatus(7L, ScooterStatus.RESERVED, StateChangeCause.USER);
    }

    @Test
    void activeTripOrBookingPreventsAnotherReservation() {
        when(trips.existsByRiderIdAndEndedAtIsNull(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.book(1L, 7L)).isInstanceOf(ConflictException.class);
        when(trips.existsByRiderIdAndEndedAtIsNull(1L)).thenReturn(false);
        when(bookings.findByRiderIdAndExpiresAtAfter(1L, now))
                .thenReturn(List.of(new TripBooking(8L, 1L, now.plusSeconds(1))));
        assertThatThrownBy(() -> service.book(1L, 7L)).isInstanceOf(ConflictException.class);
        verify(bookings, never()).save(any());
    }

    @Test
    void reservedScooterAndMissingTelemetryAreRejected() {
        scooter.changeStatus(ScooterStatus.RESERVED);
        assertThatThrownBy(() -> service.book(1L, 7L)).isInstanceOf(ConflictException.class);
        scooter.changeStatus(ScooterStatus.AVAILABLE);
        when(states.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.book(1L, 7L)).isInstanceOf(ConflictException.class);
        verify(bookings, never()).save(any());
    }

    @Test
    void cancellationBeforeExpiryReleasesTheScooter() {
        scooter.changeStatus(ScooterStatus.RESERVED);
        TripBooking booking = new TripBooking(7L, 1L, now.plusNanos(1));
        when(bookings.findById(7L)).thenReturn(Optional.of(booking));
        service.cancel(1L, 7L);
        verify(bookings).delete(booking);
        verify(lifecycle).changeStatus(7L, ScooterStatus.AVAILABLE, StateChangeCause.USER);
    }

    @Test
    void cancellationCannotTouchAnotherRidersBookingOrAnExpiredBooking() {
        when(bookings.findById(7L)).thenReturn(Optional.of(new TripBooking(7L, 2L, now.plusSeconds(1))));
        assertThatThrownBy(() -> service.cancel(1L, 7L)).isInstanceOf(ForbiddenException.class);
        when(bookings.findById(7L)).thenReturn(Optional.of(new TripBooking(7L, 1L, now)));
        assertThatThrownBy(() -> service.cancel(1L, 7L)).isInstanceOf(ConflictException.class);
        verify(bookings, never()).delete(any());
        verifyNoInteractions(sse);
    }

    @Test
    void expiryReleasesAtTheExactBoundaryAndRecordsSystemCause() {
        scooter.changeStatus(ScooterStatus.RESERVED);
        TripBooking booking = new TripBooking(7L, 1L, now);
        when(bookings.findById(7L)).thenReturn(Optional.of(booking));
        service.expire(7L);
        verify(bookings).delete(booking);
        verify(lifecycle).changeStatus(7L, ScooterStatus.AVAILABLE, StateChangeCause.SYSTEM);
    }

    @Test
    void delayedExpiryDoesNotDeleteANewerBookingOrAnAlreadyConsumedBooking() {
        when(bookings.findById(7L)).thenReturn(Optional.of(new TripBooking(7L, 2L, now.plusSeconds(600))));
        service.expire(7L);
        when(bookings.findById(7L)).thenReturn(Optional.empty());
        service.expire(7L);
        verify(bookings, never()).delete(any());
        verifyNoInteractions(lifecycle);
    }

    @Test
    void expiryAndCancellationPreserveMaintenance() {
        scooter.changeStatus(ScooterStatus.MAINTENANCE);
        when(bookings.findById(7L)).thenReturn(Optional.of(new TripBooking(7L, 1L, now)));
        service.expire(7L);
        when(bookings.findById(7L)).thenReturn(Optional.of(new TripBooking(7L, 1L, now.plusSeconds(1))));
        service.cancel(1L, 7L);
        assertThat(scooter.getStatus()).isEqualTo(ScooterStatus.MAINTENANCE);
        verifyNoInteractions(lifecycle);
    }

    @Test
    void expiredReservationCanBeReplacedWithoutWaitingForTheJob() {
        scooter.changeStatus(ScooterStatus.RESERVED);
        when(bookings.findById(7L)).thenReturn(Optional.of(new TripBooking(7L, 2L, now)));
        TripBooking booking = service.book(1L, 7L);
        assertThat(booking.getRiderId()).isEqualTo(1L);
        var order = inOrder(lifecycle);
        order.verify(lifecycle).changeStatus(7L, ScooterStatus.AVAILABLE, StateChangeCause.SYSTEM);
        order.verify(lifecycle).changeStatus(7L, ScooterStatus.RESERVED, StateChangeCause.USER);
    }

    @Test
    void findsExpiredCandidatesUsingTheInjectedClock() {
        when(bookings.findExpiredScooterIds(now)).thenReturn(List.of(7L));
        assertThat(service.expiredScooters()).containsExactly(7L);
    }

    @Test
    void unknownRiderPolicyScooterAndBookingAreRejected() {
        when(riders.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.book(2L, 7L)).isInstanceOf(ForbiddenException.class);
        when(policies.findById(RiderCategory.WORKER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.book(1L, 7L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.cancel(1L, 99L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.cancel(1L, 7L)).isInstanceOf(NotFoundException.class);
        service.expire(99L);
        verify(bookings, never()).delete(any());
    }
}
