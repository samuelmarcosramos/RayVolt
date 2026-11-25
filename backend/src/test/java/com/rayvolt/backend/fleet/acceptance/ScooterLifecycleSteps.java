package com.rayvolt.backend.fleet.acceptance;

import com.rayvolt.backend.common.exception.ConflictException;
import com.rayvolt.backend.fleet.model.*;
import com.rayvolt.backend.fleet.repository.FleetRepository;
import com.rayvolt.backend.fleet.repository.ScooterStateHistoryRepository;
import com.rayvolt.backend.fleet.service.FleetService;
import io.cucumber.datatable.DataTable;
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
    private final List<ScooterStateChange> expectedHistory = new ArrayList<>();
    private Scooter scooter;

    @Given("a scooter in state {word}")
    public void initialState(String state) {
        reset(repository, history);
        recorded.clear();
        scooter = new Scooter(7L, "Standard", 1L, ScooterStatus.valueOf(state));
        when(repository.findByIdForUpdate(7L)).thenAnswer(call -> Optional.of(scooter));
        when(repository.save(any())).thenAnswer(call -> {
            scooter = call.getArgument(0);
            return scooter;
        });
        doAnswer(call -> { recorded.add(call.getArgument(0)); return null; }).when(history).append(any());
    }

    @When("the scooter follows these lifecycle changes")
    public void followChanges(DataTable table) {
        ScooterStatus previous = ScooterStatus.AVAILABLE;
        for (var row : table.asMaps()) {
            ScooterStatus target = ScooterStatus.valueOf(row.get("state"));
            StateChangeCause cause = StateChangeCause.valueOf(row.get("cause"));
            expectedHistory.add(new ScooterStateChange(null, 7L, previous, target, NOW, cause));
            service.changeStatus(7L, target, cause);
            assertThat(scooter.status()).as("Persisted state after %s -> %s", previous, target).isEqualTo(target);
            previous = target;
        }
    }

    @Then("the scooter is retired and every change has its expected audit record")
    public void verifyHistory() {
        assertThat(scooter.status()).isEqualTo(ScooterStatus.RETIRED);
        assertThat(recorded).as("One ordered audit record per change, with the supplied cause and fixed time")
                .containsExactlyElementsOf(expectedHistory);
    }

    @Then("these lifecycle changes are rejected without saving anything")
    public void forbiddenChanges(DataTable table) {
        for (var row : table.asMaps()) {
            initialState(row.get("from"));
            Scooter original = scooter;
            ScooterStatus target = ScooterStatus.valueOf(row.get("to"));
            assertThatThrownBy(() -> service.changeStatus(7L, target, StateChangeCause.OPERATOR))
                    .as("Forbidden transition %s -> %s", original.status(), target)
                    .isInstanceOf(ConflictException.class);
            assertThat(scooter).as("Rejected transitions preserve the persisted scooter").isEqualTo(original);
            verify(repository, never()).save(any());
            verify(history, never()).append(any());
        }
    }
}
