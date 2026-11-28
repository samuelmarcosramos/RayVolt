package com.rayvolt.backend.controller;

import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.service.TripService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TripController.class)
@Tag("PT-17")
class TripControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TripService service;

    @Test
    void endReturnsSavedTelemetryAndCharges() throws Exception {
        Instant now = Instant.parse("2026-11-25T10:00:00Z");
        Trip trip = new Trip(7L, now.minusSeconds(60), 1L, 0, 30, BigDecimal.ONE);
        trip.end(now, new ScooterState(new TelemetryReading(7L, now, 80, false, 0,
                null, 40, -8, PositionStatus.OUTSIDE_HUB, false), now), null);
        when(service.end(3L)).thenReturn(trip);
        mvc.perform(post("/api/trips/3/end")).andExpect(status().isOk())
                .andExpect(jsonPath("$.endedAt").value(now.toString()))
                .andExpect(jsonPath("$.endLat").value(40))
                .andExpect(jsonPath("$.kmRidden").value(0))
                .andExpect(jsonPath("$.noHubCharge").value(5));
    }

    @Test
    void unknownTripReturns404() throws Exception {
        when(service.end(3L)).thenThrow(new NotFoundException("Trip not found"));
        mvc.perform(post("/api/trips/3/end")).andExpect(status().isNotFound());
    }

    @Test
    void repeatedEndReturns409() throws Exception {
        when(service.end(3L)).thenThrow(new ConflictException("Trip has already ended"));
        mvc.perform(post("/api/trips/3/end")).andExpect(status().isConflict());
    }
}
