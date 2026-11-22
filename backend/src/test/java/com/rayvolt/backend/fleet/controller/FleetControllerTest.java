package com.rayvolt.backend.fleet.controller;

import com.rayvolt.backend.fleet.model.*;
import com.rayvolt.backend.fleet.service.FleetService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.NoSuchElementException;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FleetController.class)
@Tag("PT-32")
class FleetControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private FleetService service;
    private final UUID id = UUID.randomUUID();
    private final UUID hub = UUID.randomUUID();

    private String request() {
        return "{\"type\":\"Standard\",\"hubId\":\"" + hub + "\"}";
    }

    @Test
    void createReturnsLocationAndAvailableScooter() throws Exception {
        when(service.add("Standard", hub)).thenReturn(new Scooter(id, "Standard", hub, ScooterStatus.AVAILABLE));
        mvc.perform(post("/api/scooters").contentType("application/json").content(request()))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/scooters/" + id))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void editReturnsUpdatedScooter() throws Exception {
        when(service.edit(id, "Standard", hub)).thenReturn(new Scooter(id, "Standard", hub, ScooterStatus.AVAILABLE));
        mvc.perform(put("/api/scooters/" + id).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hubId").value(hub.toString()));
    }

    @Test
    void retirementReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/scooters/" + id)).andExpect(status().isNoContent());
        verify(service).retire(id);
    }

    @Test
    void missingScooterReturnsNotFound() throws Exception {
        doThrow(new NoSuchElementException("Scooter not found")).when(service).retire(id);
        mvc.perform(delete("/api/scooters/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Scooter not found"));
    }

    @Test
    void fullHubReturnsConflict() throws Exception {
        when(service.add("Standard", hub)).thenThrow(new IllegalStateException("Destination hub is full"));
        mvc.perform(post("/api/scooters").contentType("application/json").content(request()))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRequestsNeverReachService() throws Exception {
        for (String body : new String[]{"{}", "{\"type\":\" \"}",
                "{\"type\":\"Standard\",\"hubId\":\"invalid\"}", "null"}) {
            mvc.perform(post("/api/scooters").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(delete("/api/scooters/invalid")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
