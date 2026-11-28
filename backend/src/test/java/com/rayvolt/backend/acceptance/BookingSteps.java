package com.rayvolt.backend.acceptance;

import com.rayvolt.backend.billing.*;
import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.repository.*;
import com.rayvolt.backend.service.*;
import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.rayvolt.backend.acceptance.LifecycleSpringConfiguration.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class BookingSteps {
    @Autowired Clock clock;
    @Autowired BookingService service;
    @Autowired TripStartService starts;
    @Autowired TelemetryService telemetry;
    @Autowired TripBookingRepository bookings;
    @Autowired TripRepository trips;
    @Autowired TripRiderRepository riders;
    @Autowired TripPolicyRepository policies;
    @Autowired ScooterRepository scooters;
    @Autowired ScooterStateRepository states;
    @Autowired DepositAccountRepository accounts;
    @Autowired CommandService commands;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;
    private Long firstScooter;
    private Long secondScooter;
    private ResultActions response;

    @Given("two synthetic riders and two available scooters for bookings")
    public void fixture() {
        Long hubId = jdbc.sql("SELECT id FROM hubs WHERE name = 'Main Gate'").query(Long.class).single();
        for (long id : List.of(8201L, 8202L)) {
            riders.save(new TripRider(id, RiderCategory.CONTRACTOR, true, LocalDate.of(2026, 12, 31)));
            accounts.save(new DepositAccount(id, new BigDecimal("40.00")));
        }
        policies.save(new TripPolicy(RiderCategory.CONTRACTOR, LocalTime.MIN, LocalTime.MIN,
                "Europe/Lisbon", 10, 30, new BigDecimal("0.50"), Set.of("Standard")));
        firstScooter = addScooter(hubId);
        secondScooter = addScooter(hubId);
    }

    private Long addScooter(Long hubId) {
        Long id = scooters.save(new Scooter("Standard", hubId)).getId();
        states.save(new ScooterState(new TelemetryReading(id, NOW, 80, true, 0, hubId,
                40, -8, PositionStatus.AT_HUB, false), NOW));
        return id;
    }

    @When("the first rider books the first scooter")
    public void book() throws Exception { requestBooking(8201L, firstScooter); }

    @Given("the first rider has booked the first scooter")
    public void booked() { service.book(8201L, firstScooter); }

    private void requestBooking(Long riderId, Long scooterId) throws Exception {
        response = mvc.perform(post("/api/bookings").principal(() -> riderId.toString())
                .contentType("application/json").content("{\"scooterId\":" + scooterId + "}"));
    }

    @Then("booking request returns {int}")
    public void responseStatus(int statusCode) throws Exception { response.andExpect(status().is(statusCode)); }

    @Then("the first scooter is reserved by the first rider until exactly ten minutes after booking")
    public void reserved() {
        TripBooking booking = bookings.findById(firstScooter).orElseThrow();
        assertThat(booking.getRiderId()).isEqualTo(8201L);
        assertThat(booking.getExpiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(scooters.findById(firstScooter).orElseThrow().getStatus()).isEqualTo(ScooterStatus.RESERVED);
    }

    @Then("booking and cancellation have not charged or unlocked either scooter")
    public void noCharge() {
        assertThat(jdbc.sql("SELECT COUNT(*) FROM deposit_holds").query(Integer.class).single()).isZero();
        for (long id : List.of(8201L, 8202L)) {
            assertThat(accounts.findById(id).orElseThrow().getAvailableBalance()).isEqualByComparingTo("40.00");
        }
        verify(commands, never()).send(any(), any());
    }

    @When("the booking clock advances by {int} milliseconds")
    public void advance(int milliseconds) { ((AdjustableClock) clock).set(NOW.plusMillis(milliseconds)); }

    @When("the booking expiry job runs")
    public void expire() { new BookingExpiryJob(service).expireBookings(); }

    @Then("the first scooter has state {word} and {int} persisted bookings")
    public void stored(String state, int count) {
        assertThat(scooters.findById(firstScooter).orElseThrow().getStatus()).isEqualTo(ScooterStatus.valueOf(state));
        assertThat(bookings.count()).isEqualTo(count);
    }

    @When("the {word} rider cancels the first scooter booking")
    public void cancel(String rider) throws Exception {
        response = mvc.perform(delete("/api/bookings/" + firstScooter).principal(
                () -> rider.equals("first") ? "8201" : "8202"));
    }

    @When("the first rider books the second scooter")
    public void bookSecond() throws Exception { requestBooking(8201L, secondScooter); }

    @Given("the first rider has an active trip on the first scooter")
    public void activeTrip() { starts.start(8201L, firstScooter); }

    @Then("the rider still has exactly one active trip and no bookings")
    public void oneTrip() {
        assertThat(trips.count()).isEqualTo(1);
        assertThat(trips.existsByRiderIdAndEndedAtIsNull(8201L)).isTrue();
        assertThat(bookings.count()).isZero();
        assertThat(scooters.findById(secondScooter).orElseThrow().getStatus()).isEqualTo(ScooterStatus.AVAILABLE);
    }

    @When("the first rider starts the booked scooter")
    public void startBooked() { starts.start(8201L, firstScooter); }

    @When("a delayed expiry check runs for the first scooter")
    public void delayedCheck() { service.expire(firstScooter); }

    @When("the second rider replaces the expired booking")
    public void replace() { service.book(8202L, firstScooter); }

    @Then("the replacement booking remains owned by the second rider")
    public void replacementSurvives() {
        TripBooking booking = bookings.findById(firstScooter).orElseThrow();
        assertThat(booking.getRiderId()).isEqualTo(8202L);
        assertThat(booking.getExpiresAt()).isEqualTo(NOW.plusSeconds(1200));
        assertThat(scooters.findById(firstScooter).orElseThrow().getStatus()).isEqualTo(ScooterStatus.RESERVED);
    }

    @When("telemetry reports a fault on the reserved scooter")
    public void fault() { telemetry.recordFault(firstScooter); }

    @When("two riders book the same scooter concurrently")
    public void sameScooter() throws Exception {
        race(() -> service.book(8201L, firstScooter), () -> service.book(8202L, firstScooter));
    }

    @When("one rider books two scooters concurrently")
    public void sameRider() throws Exception {
        race(() -> service.book(8201L, firstScooter), () -> service.book(8201L, secondScooter));
    }

    @When("one rider books and starts different scooters concurrently")
    public void bookAndStart() throws Exception {
        race(() -> service.book(8201L, firstScooter), () -> starts.start(8201L, secondScooter));
    }

    private void race(Runnable first, Runnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> a = executor.submit(() -> attempt(start, first));
            Future<Boolean> b = executor.submit(() -> attempt(start, second));
            start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .as("Only one competing reservation or trip succeeds").containsExactlyInAnyOrder(true, false);
        }
        assertThat(bookings.count() + trips.count()).as("One active booking or trip").isEqualTo(1);
    }

    private boolean attempt(CountDownLatch start, Runnable action) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
        try { action.run(); return true; }
        catch (ConflictException exception) { return false; }
    }
}
