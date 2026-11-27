package com.rayvolt.backend.controller;

import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.model.PositionStatus;
import com.rayvolt.backend.model.ScooterLive;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.service.SseService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SseController.class)
@Tag("PT-46")
class SseControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private SseService service;

    private final ScooterLive live = new ScooterLive(7L, ScooterStatus.MAINTENANCE, 87, 40.61, -8.71,
            PositionStatus.AT_HUB, "Main Gate", true, Instant.parse("2026-11-24T10:00:05Z"));

    @Test
    void stateOfAllScootersHasTheAgreedFields() throws Exception {
        when(service.list()).thenReturn(List.of(live));
        mvc.perform(get("/api/scooters/state")).andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"scooterId": 7, "status": "MAINTENANCE", "battery": 87, "lat": 40.61, "lon": -8.71,
                          "positionStatus": "AT_HUB", "hubName": "Main Gate", "fault": true, "lastSeen": "2026-11-24T10:00:05Z"}]""", true));
    }

    @Test
    void scooterWithoutTelemetryHasNullFields() throws Exception {
        when(service.get(2L)).thenReturn(new ScooterLive(2L, ScooterStatus.AVAILABLE, null, null, null, null, null,
                false, null));
        mvc.perform(get("/api/scooters/2/state")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.battery").isEmpty())
                .andExpect(jsonPath("$.hubName").isEmpty())
                .andExpect(jsonPath("$.lastSeen").isEmpty());
    }

    @Test
    void stateOfUnknownScooterIs404() throws Exception {
        when(service.get(9L)).thenThrow(new NotFoundException("Scooter not found"));
        mvc.perform(get("/api/scooters/9/state")).andExpect(status().isNotFound());
    }
}
