package com.rayvolt.backend.fleet.acceptance;

import com.rayvolt.backend.common.exception.ConflictException;
import com.rayvolt.backend.fleet.model.*;
import com.rayvolt.backend.fleet.repository.FleetRepository;
import com.rayvolt.backend.fleet.repository.ScooterStateHistoryRepository;
import com.rayvolt.backend.fleet.service.FleetService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ScooterLifecycleSteps {
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private final FleetRepository repository = mock(FleetRepository.class);
    private final ScooterStateHistoryRepository history = mock(ScooterStateHistoryRepository.class);
    private final FleetService service = new FleetService(repository, history, Clock.fixed(NOW, ZoneOffset.UTC));
    private final List<ScooterStateChange> recorded = new ArrayList<>();
    private Scooter scooter;
    private ScooterStatus initial;
    private Throwable rejected;

    @Given("a scooter in state {word}")
    public void initialState(String state) {
        initial = ScooterStatus.valueOf(state);
        scooter = new Scooter(7L, "Standard", 1L, initial);
        when(repository.findByIdForUpdate(7L)).thenAnswer(call -> Optional.of(scooter));
        when(repository.save(any())).thenAnswer(call -> {
            scooter = call.getArgument(0);
            return scooter;
        });
        doAnswer(call -> { recorded.add(call.getArgument(0)); return null; }).when(history).append(any());
    }

    @When("its state is changed to {word} because of {word}")
    public void changeState(String target, String cause) {
        try {
            service.changeStatus(7L, ScooterStatus.valueOf(target), StateChangeCause.valueOf(cause));
        } catch (ConflictException exception) {
            rejected = exception;
        }
    }

    @Then("the scooter is in state {word}")
    public void stateIs(String expected) {
        assertThat(scooter.status()).as("Persisted scooter state follows the lifecycle").isEqualTo(ScooterStatus.valueOf(expected));
    }

    @Then("one state change is recorded with the fixed time and cause {word}")
    public void recordedChange(String cause) {
        assertThat(rejected).as("Allowed transitions succeed").isNull();
        assertThat(recorded).as("Exactly one audit record per successful change").containsExactly(
                new ScooterStateChange(null, 7L, initial, scooter.status(), NOW, StateChangeCause.valueOf(cause)));
    }

    @Then("the change is rejected as a conflict")
    public void conflict() {
        assertThat(rejected).as("Invalid changes map to HTTP 409").isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Then("no state change is recorded")
    public void noChange() {
        assertThat(recorded).as("Rejected transitions do not create history").isEmpty();
    }
}
