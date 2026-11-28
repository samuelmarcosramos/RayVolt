package com.rayvolt.backend.service;

import org.junit.jupiter.api.*;
import java.util.List;
import static org.mockito.Mockito.*;

@Tag("PT-15")
class BookingExpiryJobTest {
    @Test
    void oneFailedExpiryDoesNotPreventOtherBookingsFromExpiring() {
        BookingService bookings = mock(BookingService.class);
        when(bookings.expiredScooters()).thenReturn(List.of(7L, 8L));
        doThrow(new IllegalStateException("Synthetic expiry failure")).when(bookings).expire(7L);
        new BookingExpiryJob(bookings).expireBookings();
        verify(bookings).expire(7L);
        verify(bookings).expire(8L);
    }
}
