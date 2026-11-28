package com.rayvolt.backend.controller;

import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.Trip;
import com.rayvolt.backend.service.TripStartService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TripStartController.class)
@Tag("PT-16")
class TripStartControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TripStartService service;

    @Test
    void createsTripForTheAuthenticatedRiderAndIgnoresClientPolicyFields() throws Exception {
        Instant now = Instant.parse("2026-11-25T10:00:00Z");
        when(service.start(1L, 7L)).thenReturn(new Trip(1L, 7L, now, 2L, 0, 30, BigDecimal.ONE));
        mvc.perform(post("/api/trips").principal(() -> "1").contentType("application/json")
                .content("{\"scooterId\":7,\"riderId\":99,\"category\":\"WORKER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.riderId").value(1))
                .andExpect(jsonPath("$.scooterId").value(7)).andExpect(jsonPath("$.startHubId").value(2))
                .andExpect(jsonPath("$.startedAt").value(now.toString()));
        verify(service).start(1L, 7L);
    }

    @Test
    void anonymousAndInvalidIdentitiesReturn401() throws Exception {
        mvc.perform(post("/api/trips").contentType("application/json").content("{\"scooterId\":7}"))
                .andExpect(status().isUnauthorized());
        for (String name : List.of("someone", "0", "-1")) {
            mvc.perform(post("/api/trips").principal(() -> name).contentType("application/json")
                    .content("{\"scooterId\":7}")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void invalidScooterIdsReturn400() throws Exception {
        for (String body : List.of("{}", "{\"scooterId\":0}", "{\"scooterId\":-1}")) {
            mvc.perform(post("/api/trips").principal(() -> "1").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void forbiddenTypeReturns403AndFailedDepositReturns409() throws Exception {
        when(service.start(1L, 7L)).thenThrow(new ForbiddenException("Type is not allowed"));
        mvc.perform(post("/api/trips").principal(() -> "1").contentType("application/json")
                .content("{\"scooterId\":7}")).andExpect(status().isForbidden());
        doThrow(new ConflictException("Deposit hold failed")).when(service).start(1L, 7L);
        mvc.perform(post("/api/trips").principal(() -> "1").contentType("application/json")
                .content("{\"scooterId\":7}")).andExpect(status().isConflict());
    }
}
