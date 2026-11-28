package com.rayvolt.backend.acceptance;

import com.rayvolt.backend.model.*;
import com.rayvolt.backend.repository.*;
import com.rayvolt.backend.service.*;
import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.math.BigDecimal;
import com.rayvolt.backend.errors.ConflictException;
import java.util.List;
import java.util.concurrent.*;
import static com.rayvolt.backend.acceptance.LifecycleSpringConfiguration.NOW;
import static org.assertj.core.api.Assertions.*;

public class TripSteps {
    @Autowired TripService service;
    @Autowired TripRepository trips;
    @Autowired ScooterRepository scooters;
    @Autowired ScooterStateRepository states;
    @Autowired JdbcClient jdbc;
    private Long id;
    private Trip ended;

    @Given("a persisted trip lasting {int} seconds ending {word} with damage {word}")
    public void trip(int seconds, String location, String damage) {
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (2, 'Return hub', 10)").update();
        Scooter scooter = scooters.save(new Scooter("Standard", 1L));
        scooter.changeStatus(ScooterStatus.IN_USE);
        scooters.save(scooter);
        id = trips.save(new Trip(scooter.getId(), NOW.minusSeconds(seconds), 1L, 0,
                30, new BigDecimal("0.50"))).getId();
        boolean atHub = location.equals("at-hub");
        ScooterState state = new ScooterState(new TelemetryReading(scooter.getId(), NOW.minusSeconds(1),
                80, false, 10, null, 40, -8, PositionStatus.MOVING, false), NOW.minusSeconds(1));
        state.update(new TelemetryReading(scooter.getId(), NOW, 80, false, 0, atHub ? 2L : null,
                40.001, -8, atHub ? PositionStatus.AT_HUB : PositionStatus.OUTSIDE_HUB,
                Boolean.parseBoolean(damage)), NOW);
        states.save(state);
    }

    @When("the trip is ended")
    public void end() { ended = service.end(id); }

    @When("two requests end the trip concurrently")
    public void concurrentEnd() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> end = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try { service.end(id); return true; }
                catch (ConflictException exception) { return false; }
            };
            Future<Boolean> first = executor.submit(end);
            Future<Boolean> second = executor.submit(end);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .as("Only one end request succeeds").containsExactlyInAnyOrder(true, false);
        }
        ended = trips.findById(id).orElseThrow();
    }

    @Then("the persisted trip has end telemetry, scooter state {word}, no-hub charge {int} and {int} overtime minutes")
    public void saved(String status, int noHub, int overtime) {
        Trip saved = trips.findById(id).orElseThrow();
        assertThat(saved.getEndedAt()).isEqualTo(NOW);
        assertThat(saved.getEndLat()).isEqualTo(40.001);
        assertThat(saved.getEndLon()).isEqualTo(-8);
        assertThat(saved.getKmRidden()).isCloseTo(0.111195, within(0.000001));
        assertThat(saved.getNoHubCharge()).isEqualByComparingTo(BigDecimal.valueOf(noHub));
        assertThat(saved.getOvertimeMinutes()).isEqualTo(overtime);
        assertThat(saved.getOvertimeCharge()).isEqualByComparingTo(new BigDecimal("0.50").multiply(BigDecimal.valueOf(overtime)));
        assertThat(scooters.findById(ended.getScooterId()).orElseThrow().getStatus())
                .isEqualTo(ScooterStatus.valueOf(status));
    }
}
