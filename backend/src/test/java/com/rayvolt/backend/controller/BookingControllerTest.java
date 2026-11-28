package com.rayvolt.backend.controller;

import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.TripBooking;
import com.rayvolt.backend.service.BookingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@Tag("PT-15")
class BookingControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BookingService service;

    @Test
    void createsBookingWithServerExpiryForTheAuthenticatedRider() throws Exception {
        Instant now = Instant.parse("2026-11-25T10:00:00Z");
        when(service.book(1L, 7L)).thenReturn(TripBooking.reserve(7L, 1L, now));
        mvc.perform(post("/api/bookings").principal(() -> "1").contentType("application/json")
                .content("{\"scooterId\":7,\"riderId\":99,\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.riderId").value(1))
                .andExpect(jsonPath("$.scooterId").value(7))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-25T10:10:00Z"));
        verify(service).book(1L, 7L);
    }

    @Test
    void cancellationReturns204ForTheAuthenticatedOwner() throws Exception {
        mvc.perform(delete("/api/bookings/7").principal(() -> "1")).andExpect(status().isNoContent());
        verify(service).cancel(1L, 7L);
    }

    @Test
    void missingIdentityReturns401ForCreationAndCancellation() throws Exception {
        mvc.perform(post("/api/bookings").contentType("application/json").content("{\"scooterId\":7}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/bookings/7")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void invalidScooterIdsReturn400() throws Exception {
        for (String body : List.of("{}", "{\"scooterId\":0}", "{\"scooterId\":-1}")) {
            mvc.perform(post("/api/bookings").principal(() -> "1").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void missingBookingReturns404AndAnotherOwnerReturns403() throws Exception {
        doThrow(new NotFoundException("Booking not found")).when(service).cancel(1L, 7L);
        mvc.perform(delete("/api/bookings/7").principal(() -> "1")).andExpect(status().isNotFound());
        doThrow(new ForbiddenException("Only the booking owner can cancel it")).when(service).cancel(1L, 7L);
        mvc.perform(delete("/api/bookings/7").principal(() -> "1")).andExpect(status().isForbidden());
    }

    @Test
    void unavailableScooterReturns409() throws Exception {
        when(service.book(1L, 7L)).thenThrow(new ConflictException("Scooter is reserved"));
        mvc.perform(post("/api/bookings").principal(() -> "1").contentType("application/json")
                .content("{\"scooterId\":7}")).andExpect(status().isConflict());
    }
}
