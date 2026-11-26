package com.rayvolt.backend.fleet.acceptance;

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

    @Before
    void seedSyntheticFleet() {
        jdbc.sql("DELETE FROM scooters").update();
        jdbc.sql("DELETE FROM hubs").update();
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES "
                + "(1, 'North Gate', 10), (2, 'South Gate', 10)").update();
        jdbc.sql("INSERT INTO scooters (id, type, hub_id, status, battery_percent) VALUES "
                + "(1, 'Standard', 1, 'AVAILABLE', 10), "
                + "(2, 'Standard', 1, 'AVAILABLE', 9), "
                + "(3, 'Cargo', 1, 'AVAILABLE', 80), "
                + "(4, 'Patrol', 1, 'IN_USE', 90), "
                + "(5, 'Standard', 2, 'AVAILABLE', 99), "
                + "(6, 'Patrol', 1, 'AVAILABLE', 50), "
                + "(7, 'Standard', 1, 'RETIRED', 100)").update();
    }

    @When("the user searches hub {int} without a type")
    void searchHubWithoutType(int hubId) throws Exception {
        result = mvc.perform(get("/api/scooters/available").param("hubId", String.valueOf(hubId)));
    }

    @When("the user searches hub {int} for {string} scooters with minimum battery {int}")
    void searchHubByTypeAndBattery(int hubId, String type, int minimumBattery) throws Exception {
        result = mvc.perform(get("/api/scooters/available")
                        .param("hubId", String.valueOf(hubId))
                        .param("type", type)
                        .param("minBatteryPercent", String.valueOf(minimumBattery)));
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
