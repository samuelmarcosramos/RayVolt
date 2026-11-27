package com.rayvolt.backend.acceptance;

import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ScooterAvailabilitySteps {
    @Autowired private JdbcClient jdbc;
    @Autowired private MockMvc mvc;
    private ResultActions result;
    private Long mainHub;
    private Long productionHub;

    @Before
    void seedSyntheticFleet() {
        jdbc.sql("DELETE FROM scooter_state_history").update();
        jdbc.sql("DELETE FROM scooter_state").update();
        jdbc.sql("DELETE FROM scooters").update();
        mainHub = hubId("Main Gate");
        productionHub = hubId("Production Hall");
        jdbc.sql("INSERT INTO scooters (id, type, hub_id, status) VALUES "
                + "(7001, 'Standard', :mainHub, 'AVAILABLE'), "
                + "(7002, 'Standard', :mainHub, 'AVAILABLE'), "
                + "(7003, 'Cargo', :mainHub, 'AVAILABLE'), "
                + "(7004, 'Patrol', :mainHub, 'IN_USE'), "
                + "(7005, 'Standard', :productionHub, 'AVAILABLE'), "
                + "(7006, 'Patrol', :mainHub, 'AVAILABLE'), "
                + "(7007, 'Standard', :mainHub, 'RETIRED')")
                .param("mainHub", mainHub).param("productionHub", productionHub).update();
        addTelemetry(7001, mainHub, 10);
        addTelemetry(7002, mainHub, 9);
        addTelemetry(7003, mainHub, 80);
        addTelemetry(7004, mainHub, 90);
        addTelemetry(7005, productionHub, 99);
        addTelemetry(7006, mainHub, 50);
    }

    private Long hubId(String name) {
        return jdbc.sql("SELECT id FROM hubs WHERE name = :name").param("name", name).query(Long.class).single();
    }

    private void addTelemetry(long scooterId, Long hubId, int battery) {
        jdbc.sql("""
                INSERT INTO scooter_state
                    (scooter_id, reading_at, received_at, battery, locked, speed_kmh, hub_id, lat, lon,
                     position_status, fault, odometer_km)
                VALUES (:id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :battery, false, 0, :hub, 0, 0,
                        'AT_HUB', false, 0)
                """).param("id", scooterId).param("battery", battery).param("hub", hubId).update();
    }

    @When("the user searches hub {string} without a type")
    void searchHubWithoutType(String name) throws Exception {
        result = mvc.perform(get("/api/scooters/available").param("hubId", String.valueOf(hubId(name))));
    }

    @When("the user searches hub {string} for {string} scooters")
    void searchHubByType(String name, String type) throws Exception {
        Long hubId = hubId(name);
        result = mvc.perform(get("/api/scooters/available")
                        .param("hubId", String.valueOf(hubId))
                        .param("type", type));
    }

    @Then("the availability response contains scooter IDs {string}")
    void responseContainsScooters(String expectedIds) throws Exception {
        Integer[] ids = java.util.Arrays.stream(expectedIds.split(","))
                .map(String::trim).map(Integer::parseInt).toArray(Integer[]::new);
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(ids)));
    }

    @Then("the availability response is empty")
    void responseIsEmpty() throws Exception {
        result.andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
