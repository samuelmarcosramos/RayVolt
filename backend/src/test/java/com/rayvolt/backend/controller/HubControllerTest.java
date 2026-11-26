package com.rayvolt.backend.controller;

import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.service.FleetService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HubController.class)
@Tag("PT-14")
class HubControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private FleetService service;

    @Test
    void listsHubsForScooterSearch() throws Exception {
        when(service.listHubs()).thenReturn(List.of(new Hub(3L, "North Gate", 10, null, null)));

        mvc.perform(get("/api/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("North Gate"));
    }
}
