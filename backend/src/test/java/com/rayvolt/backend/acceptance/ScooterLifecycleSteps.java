package com.rayvolt.backend.acceptance;

import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.service.FleetService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.List;
import static com.rayvolt.backend.acceptance.LifecycleSpringConfiguration.NOW;
import static org.assertj.core.api.Assertions.*;

public class ScooterLifecycleSteps {
    @Autowired private FleetService service;
    @Autowired private JdbcClient jdbc;
    private Long scooterId;
    private ScooterStatus original;
    private ScooterStatus target;
    private StateChangeCause cause;
    private List<ScooterStateChange> before;
    private ConflictException rejection;

    @Before
    public void cleanDatabase() {
        jdbc.sql("DELETE FROM trips").update();
        jdbc.sql("DELETE FROM scooter_state_history").update();
        jdbc.sql("DELETE FROM scooter_state").update();
        jdbc.sql("DELETE FROM scooters").update();
        jdbc.sql("DELETE FROM hubs").update();
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (1, 'Synthetic hub', 2)").update();
    }

    @Given("a persisted scooter in state {word}")
    public void initialState(String value) {
        ScooterStatus state = ScooterStatus.valueOf(value);
        scooterId = service.add("Standard", 1L).getId();
        if (state != ScooterStatus.AVAILABLE) {
            service.changeStatus(scooterId, state, StateChangeCause.OPERATOR);
        }
        original = state;
        before = service.history(scooterId);
    }

    @When("the application changes its state to {word} with cause {word}")
    public void changeState(String state, String reason) {
        target = ScooterStatus.valueOf(state);
        cause = StateChangeCause.valueOf(reason);
        try {
            service.changeStatus(scooterId, target, cause);
        } catch (ConflictException exception) {
            rejection = exception;
        }
    }

    @Then("the new state and its timestamped cause are saved")
    public void verifyHistory() {
        assertThat(rejection).as("Allowed transitions succeed").isNull();
        assertThat(service.get(scooterId).getStatus()).isEqualTo(target);
        List<ScooterStateChange> changes = service.history(scooterId);
        assertThat(changes).hasSize(before.size() + 1);
        assertThat(changes.subList(0, before.size())).usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(before);
        assertThat(changes).extracting(ScooterStateChange::getId).isSorted().doesNotHaveDuplicates();
        assertThat(changes.getLast()).usingRecursiveComparison().ignoringFields("id")
                .isEqualTo(new ScooterStateChange(scooterId, original, target, NOW, cause));
    }

    @Then("the change is rejected and the stored state and history stay unchanged")
    public void verifyRejection() {
        assertThat(rejection).as("Forbidden transitions raise a conflict").isNotNull();
        assertThat(service.get(scooterId).getStatus()).isEqualTo(original);
        assertThat(service.history(scooterId)).usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(before);
    }
}
