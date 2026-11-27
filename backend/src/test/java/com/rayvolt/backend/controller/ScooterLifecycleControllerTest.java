package com.rayvolt.backend.controller;

import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.service.FleetService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScooterLifecycleController.class)
@Tag("PT-34")
class ScooterLifecycleControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private FleetService service;

    @Test
    void operatorChangeReturnsTheNewState() throws Exception {
        when(service.changeOperatorStatus(7L, ScooterStatus.MAINTENANCE))
                .thenReturn(new Scooter(7L, "Standard", 1L, ScooterStatus.MAINTENANCE));
        mvc.perform(patch("/api/scooters/7/state").contentType("application/json")
                        .content("{\"status\":\"MAINTENANCE\",\"cause\":\"TELEMETRY\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    void historyReturnsStateTimeAndCause() throws Exception {
        when(service.history(7L)).thenReturn(List.of(new ScooterStateChange(7L,
                ScooterStatus.AVAILABLE, ScooterStatus.RESERVED, Instant.parse("2026-11-25T10:00:00Z"),
                StateChangeCause.USER)));
        mvc.perform(get("/api/scooters/7/state-history")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].toStatus").value("RESERVED"))
                .andExpect(jsonPath("$[0].changedAt").value("2026-11-25T10:00:00Z"))
                .andExpect(jsonPath("$[0].cause").value("USER"));
    }

    @Test
    void forbiddenChangeReturnsConflictProblemDetail() throws Exception {
        when(service.changeOperatorStatus(7L, ScooterStatus.AVAILABLE))
                .thenThrow(new ConflictException("Cannot change scooter state from RETIRED to AVAILABLE"));
        mvc.perform(patch("/api/scooters/7/state").contentType("application/json")
                        .content("{\"status\":\"AVAILABLE\",\"cause\":\"OPERATOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void missingStatusNeverReachesTheService() throws Exception {
        for (String body : List.of("{}", "{\"status\":null}")) {
            mvc.perform(patch("/api/scooters/7/state").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        verifyNoInteractions(service);
    }

}
