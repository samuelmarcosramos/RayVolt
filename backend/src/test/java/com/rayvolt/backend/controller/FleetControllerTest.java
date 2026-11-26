package com.rayvolt.backend.controller;

import com.rayvolt.backend.model.*;
import com.rayvolt.backend.service.FleetService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.errors.ConflictException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FleetController.class)
@Tag("PT-32")
class FleetControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private FleetService service;
    private final Long id = 1L;
    private final Long hub = 2L;

    private String request() {
        return "{\"type\":\"Standard\",\"hubId\":" + hub + "}";
    }

    @Test
    void listReturnsEmptyArrayWhenFleetIsEmpty() throws Exception {
        when(service.list()).thenReturn(List.of());
        mvc.perform(get("/api/scooters")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listIncludesRetiredScootersForOperatorAndTelemetrySync() throws Exception {
        when(service.list()).thenReturn(List.of(new Scooter(id, "Standard", hub, ScooterStatus.RETIRED)));
        mvc.perform(get("/api/scooters")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.intValue()))
                .andExpect(jsonPath("$[0].type").value("Standard"))
                .andExpect(jsonPath("$[0].hubId").value(hub.intValue()))
                .andExpect(jsonPath("$[0].status").value("RETIRED"));
    }

    @Test
    void createReturnsLocationAndAvailableScooter() throws Exception {
        when(service.add("Standard", hub)).thenReturn(new Scooter(id, "Standard", hub, ScooterStatus.AVAILABLE));
        mvc.perform(post("/api/scooters").contentType("application/json").content(request()))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/scooters/" + id))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void getReturnsScooterAtItsLocation() throws Exception {
        Scooter scooter = new Scooter(id, "Standard", hub, ScooterStatus.AVAILABLE);
        when(service.add("Standard", hub)).thenReturn(scooter);
        when(service.get(id)).thenReturn(scooter);
        String location = mvc.perform(post("/api/scooters").contentType("application/json").content(request()))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.intValue()))
                .andExpect(jsonPath("$.hubId").value(hub.intValue()));
    }

    @Test
    void getUnknownScooterReturnsProblemDetail() throws Exception {
        when(service.get(999L)).thenThrow(new NotFoundException("Scooter not found"));
        mvc.perform(get("/api/scooters/999")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void editReturnsUpdatedScooter() throws Exception {
        when(service.edit(id, "Standard", hub)).thenReturn(new Scooter(id, "Standard", hub, ScooterStatus.AVAILABLE));
        mvc.perform(put("/api/scooters/" + id).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hubId").value(hub.intValue()));
    }

    @Test
    void retirementReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/scooters/" + id)).andExpect(status().isNoContent());
        verify(service).retire(id);
    }

    @Test
    void missingScooterReturnsNotFound() throws Exception {
        doThrow(new NotFoundException("Scooter not found")).when(service).retire(id);
        mvc.perform(delete("/api/scooters/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Scooter not found"));
    }

    @Test
    void fullHubReturnsConflict() throws Exception {
        when(service.add("Standard", hub)).thenThrow(new ConflictException("Destination hub is full"));
        mvc.perform(post("/api/scooters").contentType("application/json").content(request()))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRequestsNeverReachService() throws Exception {
        for (String body : new String[]{"{}", "{\"type\":\" \"}",
                "{\"type\":\"Standard\",\"hubId\":\"invalid\"}", "null", "{",
                "{\"type\":\"Standard\",\"hubId\":0}", "{\"type\":\"Standard\",\"hubId\":-1}"}) {
            mvc.perform(post("/api/scooters").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(400));
        }
        mvc.perform(delete("/api/scooters/invalid")).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }
}
