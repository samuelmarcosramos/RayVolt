package com.rayvolt.backend.acceptance;

import com.rayvolt.backend.billing.*;
import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.repository.*;
import com.rayvolt.backend.service.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.rayvolt.backend.acceptance.LifecycleSpringConfiguration.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class TripStartSteps {
    @Autowired Clock clock;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;
    @Autowired TripStartService service;
    @Autowired TripRepository trips;
    @Autowired TripRiderRepository riders;
    @Autowired TripPolicyRepository policies;
    @Autowired TripBookingRepository bookings;
    @Autowired ScooterRepository scooters;
    @Autowired ScooterStateRepository states;
    @Autowired DepositAccountRepository accounts;
    @Autowired CommandService commands;
    private RiderCategory category;
    private Long scooterId;
    private Long hubId;
    private ScooterStatus originalStatus;
    private ResultActions response;

    @Before(order = -100)
    public void clearTripData() {
        ((AdjustableClock) clock).set(NOW);
        for (String table : List.of("deposit_holds", "deposit_accounts", "trip_bookings", "trips",
                "trip_riders", "trip_policy_types", "trip_policies")) {
            jdbc.sql("DELETE FROM " + table).update();
        }
        clearInvocations(commands);
    }

    @Given("a startable {word} rider and a scooter in state {word} with battery {int}")
    public void fixture(String riderCategory, String status, int battery) {
        category = RiderCategory.valueOf(riderCategory);
        hubId = jdbc.sql("SELECT id FROM hubs WHERE name = 'Main Gate'").query(Long.class).single();
        riders.save(new TripRider(8101L, category, true, LocalDate.of(2026, 12, 1)));
        riders.save(new TripRider(8102L, category, true, LocalDate.of(2026, 12, 1)));
        setPolicy("00:00", "00:00", Set.of("Standard"));
        Scooter scooter = scooters.save(new Scooter("Standard", hubId));
        originalStatus = ScooterStatus.valueOf(status);
        if (originalStatus != ScooterStatus.AVAILABLE) {
            scooter.changeStatus(originalStatus);
            scooters.save(scooter);
        }
        scooterId = scooter.getId();
        states.save(new ScooterState(new TelemetryReading(scooterId, NOW, battery, true,
                0, hubId, 40, -8, PositionStatus.AT_HUB, false), NOW));
        accounts.save(new DepositAccount(8101L, new BigDecimal("40.00")));
        accounts.save(new DepositAccount(8102L, new BigDecimal("40.00")));
    }

    private void setPolicy(String from, String until, Set<String> types) {
        policies.save(new TripPolicy(category, LocalTime.parse(from), LocalTime.parse(until),
                "Europe/Lisbon", 10, 30, new BigDecimal("0.50"), types));
    }

    @Given("the scooter reservation belongs to {word} and expires in {int} seconds")
    public void booking(String owner, int seconds) {
        bookings.save(new TripBooking(scooterId, owner.equals("self") ? 8101L : 8102L, NOW.plusSeconds(seconds)));
    }

    @Given("the rider policy allows only type {string}")
    public void types(String type) { setPolicy("00:00", "00:00", Set.of(type)); }

    @Given("the rider policy allows use from {string} until {string}")
    public void hours(String from, String until) { setPolicy(from, until, Set.of("Standard")); }

    @Given("the rider sandbox balance is {string} EUR")
    public void balance(String amount) { accounts.save(new DepositAccount(8101L, new BigDecimal(amount))); }

    @Given("the rider account expired yesterday")
    public void expired() {
        riders.save(new TripRider(8101L, category, true, LocalDate.of(2026, 11, 24)));
    }

    @When("the authenticated rider starts the scooter")
    public void start() throws Exception {
        response = mvc.perform(post("/api/trips").principal(() -> "8101").contentType("application/json")
                .content("{\"scooterId\":" + scooterId + "}"));
    }

    @Then("trip start returns {int}")
    public void responseStatus(int expected) throws Exception { response.andExpect(status().is(expected)); }

    @Then("the trip start time, hub, rider and IN_USE state are persisted with an unlock request")
    public void saved() {
        assertThat(trips.findAll()).singleElement().satisfies(trip -> {
            assertThat(trip.getStartedAt()).isEqualTo(NOW);
            assertThat(trip.getStartHubId()).isEqualTo(hubId);
            assertThat(trip.getRiderId()).isEqualTo(8101L);
            assertThat(trip.getScooterId()).isEqualTo(scooterId);
        });
        assertThat(scooters.findById(scooterId).orElseThrow().getStatus()).isEqualTo(ScooterStatus.IN_USE);
        assertThat(bookings.findById(scooterId)).isEmpty();
        verify(commands).send(scooterId, ScooterCommand.UNLOCK);
    }

    @Then("the sandbox contains {int} holds totalling {string} EUR")
    public void holds(int count, String amount) {
        assertThat(jdbc.sql("SELECT COUNT(*) FROM deposit_holds").query(Integer.class).single()).isEqualTo(count);
        BigDecimal total = jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM deposit_holds").query(BigDecimal.class).single();
        assertThat(total).isEqualByComparingTo(amount);
        if (count > 0) {
            assertThat(jdbc.sql("SELECT trip_id FROM deposit_holds").query(Long.class).single())
                    .isEqualTo(trips.findAll().getFirst().getId());
        }
    }

    @Then("no trip is saved and the scooter state and sandbox balance {string} stay unchanged")
    public void rejected(String balance) {
        assertThat(trips.count()).isZero();
        assertThat(scooters.findById(scooterId).orElseThrow().getStatus()).isEqualTo(originalStatus);
        assertThat(accounts.findById(8101L).orElseThrow().getAvailableBalance()).isEqualByComparingTo(balance);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM deposit_holds").query(Integer.class).single()).isZero();
        verify(commands, never()).send(any(), any());
    }

    @When("two riders start the same scooter concurrently")
    public void sameScooter() throws Exception { race(8101L, scooterId, 8102L, scooterId); }

    @When("the same rider starts two scooters concurrently")
    public void sameRider() throws Exception {
        Scooter second = scooters.save(new Scooter("Standard", hubId));
        states.save(new ScooterState(new TelemetryReading(second.getId(), NOW, 80, true, 0, hubId,
                40, -8, PositionStatus.AT_HUB, false), NOW));
        race(8101L, scooterId, 8101L, second.getId());
    }

    private boolean attempt(CountDownLatch start, Long riderId, Long scooterId) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
        try { service.start(riderId, scooterId); return true; }
        catch (ConflictException exception) { return false; }
    }

    private void race(Long firstRider, Long firstScooter, Long secondRider, Long secondScooter) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> attempt(start, firstRider, firstScooter));
            Future<Boolean> second = executor.submit(() -> attempt(start, secondRider, secondScooter));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .as("Only one concurrent trip start succeeds").containsExactlyInAnyOrder(true, false);
        }
        assertThat(trips.count()).isEqualTo(1);
        verify(commands, times(1)).send(any(), eq(ScooterCommand.UNLOCK));
    }
}
