package com.rayvolt.backend.controller;

import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.service.HubService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HubController.class)
@Tag("PT-33")
class HubControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HubService service;
    private final HubDetails details = new HubDetails("Gate", "Security", 2, 40.6, -8.7);
    private final String body = """
            {"name":"Gate","department":"Security","capacity":2,"lat":40.6,"lon":-8.7}
            """;

    @Test
    void createReturnsResourceAndLocation() throws Exception {
        when(service.add(details)).thenReturn(new Hub(1L, "Gate", 2, 40.6, -8.7));
        mvc.perform(post("/api/hubs").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/hubs/1"))
                .andExpect(jsonPath("$.name").value("Gate")).andExpect(jsonPath("$.lat").value(40.6));
    }

    @Test
    void listAndGetExposeHubDetails() throws Exception {
        Hub hub = new Hub(details);
        when(service.list()).thenReturn(List.of(hub));
        when(service.get(1L)).thenReturn(hub);
        mvc.perform(get("/api/hubs")).andExpect(status().isOk()).andExpect(jsonPath("$[0].department").value("Security"));
        mvc.perform(get("/api/hubs/1")).andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(2));
    }

    @Test
    void editReturnsUpdatedDetails() throws Exception {
        when(service.edit(1L, details)).thenReturn(new Hub(details));
        mvc.perform(put("/api/hubs/1").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.department").value("Security"));
    }

    @Test
    void missingAndConflictingHubsReturnProblems() throws Exception {
        when(service.get(99L)).thenThrow(new NotFoundException("Hub not found"));
        when(service.edit(1L, details)).thenThrow(new ConflictException("Capacity below occupancy"));
        when(service.add(details)).thenThrow(new ConflictException("Hub name must be unique"));
        mvc.perform(get("/api/hubs/99")).andExpect(status().isNotFound());
        mvc.perform(put("/api/hubs/1").contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/hubs").contentType("application/json").content(body)).andExpect(status().isConflict());
    }

    @Test
    void invalidRequestsNeverReachService() throws Exception {
        for (String invalid : new String[]{"{}", "null", "{", body.replace("\"capacity\":2", "\"capacity\":0"),
                body.replace("\"lat\":40.6", "\"lat\":91"), body.replace("\"department\":\"Security\",", "")}) {
            mvc.perform(post("/api/hubs").contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
            mvc.perform(put("/api/hubs/1").contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
