package com.rayvolt.backend.acceptance;

import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.service.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import static org.assertj.core.api.Assertions.*;

public class HubSteps {
    @Autowired HubService hubs;
    @Autowired FleetService fleet;
    @Autowired JdbcClient jdbc;
    private Long hubId;
    private Long scooterId;
    private RuntimeException rejection;

    @Before("@PT-33")
    public void cleanManagedHubFixtures() {
        jdbc.sql("""
                DELETE FROM scooter_state_history
                WHERE scooter_id IN (SELECT id FROM scooters
                    WHERE hub_id IN (SELECT id FROM hubs WHERE name IN ('North Gate', 'South Gate')))
                """).update();
        jdbc.sql("""
                DELETE FROM scooter_state
                WHERE scooter_id IN (SELECT id FROM scooters
                    WHERE hub_id IN (SELECT id FROM hubs WHERE name IN ('North Gate', 'South Gate')))
                """).update();
        jdbc.sql("""
                DELETE FROM scooters
                WHERE hub_id IN (SELECT id FROM hubs WHERE name IN ('North Gate', 'South Gate'))
                """).update();
        jdbc.sql("DELETE FROM hubs WHERE name IN ('North Gate', 'South Gate')").update();
    }

    @When("an operator adds hub {string} in department {string} with capacity {int} at {double}, {double}")
    public void add(String name, String department, int capacity, double lat, double lon) {
        hubId = hubs.add(new HubDetails(name, department, capacity, lat, lon)).getId();
    }

    @Then("the hub list contains {string} in {string} with capacity {int} at {double}, {double}")
    public void listed(String name, String department, int capacity, double lat, double lon) {
        assertThat(hubs.list()).filteredOn(hub -> hub.getId().equals(hubId)).singleElement().satisfies(hub -> {
            assertThat(hub.getName()).isEqualTo(name);
            assertThat(hub.getDepartment()).isEqualTo(department);
            assertThat(hub.getCapacity()).isEqualTo(capacity);
            assertThat(hub.getLat()).isEqualTo(lat);
            assertThat(hub.getLon()).isEqualTo(lon);
        });
    }

    @Given("a managed hub with 2 active scooters")
    public void occupied() {
        add("North Gate", "Security", 3, 40.6, -8.7);
        scooterId = fleet.add("Standard", hubId).getId();
        fleet.add("Standard", hubId);
    }

    @When("an operator adds another hub with the same name")
    public void duplicate() {
        rejection = catchThrowableOfType(ConflictException.class,
                () -> hubs.add(new HubDetails("North Gate", "Other", 2, 0.0, 0.0)));
    }

    @When("an operator supplies hub capacity {int}")
    public void invalid(int capacity) {
        rejection = catchThrowableOfType(BadRequestException.class,
                () -> new HubDetails("Gate", "Security", capacity, 0.0, 0.0));
    }

    @Then("the hub details are rejected")
    public void badRequest() { assertThat(rejection).isInstanceOf(BadRequestException.class); }

    @When("an operator changes its capacity to {int}")
    public void resize(int capacity) {
        try { hubs.edit(hubId, new HubDetails("North Gate", "Security", capacity, 40.6, -8.7)); }
        catch (ConflictException exception) { rejection = exception; }
    }

    @Then("the hub change is rejected as a conflict")
    public void conflict() { assertThat(rejection).isInstanceOf(ConflictException.class); }

    @Then("the stored hub capacity is {int}")
    public void capacity(int capacity) { assertThat(hubs.get(hubId).getCapacity()).isEqualTo(capacity); }

    @When("an operator updates its name, department and location")
    public void update() { hubs.edit(hubId, new HubDetails("South Gate", "Logistics", 2, -90.0, 180.0)); }

    @Given("one of its scooters is retired")
    public void retire() { fleet.retire(scooterId); }
}
