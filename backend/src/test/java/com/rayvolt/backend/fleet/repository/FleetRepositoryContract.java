package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.*;
import com.rayvolt.backend.fleet.service.FleetService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Tag("PT-32")
abstract class FleetRepositoryContract {
    @Autowired private FleetService service;
    @Autowired private PostgresFleetRepository repository;
    @Autowired private JdbcClient jdbc;
    @Autowired private TransactionTemplate transactions;
    private UUID hub;

    @BeforeEach
    void prepareHub() {
        jdbc.sql("DELETE FROM scooters").update();
        jdbc.sql("DELETE FROM hubs").update();
        hub = UUID.randomUUID();
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (:id, 'Synthetic hub', 2)").param("id", hub).update();
    }

    @Test
    void capacityBoundariesAndRetirementPersist() {
        Scooter first = service.add("Standard", hub);
        assertThat(repository.hasSpace(hub)).as("Capacity minus one has room").isTrue();
        service.add("Patrol", hub);
        assertThat(repository.hasSpace(hub)).as("Exactly full has no room").isFalse();
        assertThatThrownBy(() -> service.add("Standard", hub)).isInstanceOf(IllegalStateException.class);
        service.retire(first.id());
        assertThat(repository.findById(first.id()).orElseThrow().status()).isEqualTo(ScooterStatus.RETIRED);
        assertThat(repository.hasSpace(hub)).as("Retirement releases capacity").isTrue();
        service.add("Standard", hub);
        assertThat(jdbc.sql("SELECT count(*) FROM scooters").query(Long.class).single()).isEqualTo(3);
    }

    @Test
    void unknownHubIsRejectedAndFailedOperationRollsBack() {
        assertThatThrownBy(() -> service.add("Standard", UUID.randomUUID())).isInstanceOf(NoSuchElementException.class);
        Scooter scooter = new Scooter(UUID.randomUUID(), "Standard", hub, ScooterStatus.AVAILABLE);
        assertThatThrownBy(() -> transactions.execute(status -> {
            repository.lockHubs(hub);
            repository.save(scooter);
            throw new IllegalStateException("Synthetic failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(scooter.id())).isEmpty();
    }

    @Test
    void movesPersistAndReservedScootersCannotRetire() {
        Scooter scooter = service.add("Standard", hub);
        UUID destination = UUID.randomUUID();
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (:id, 'Destination', 1)").param("id", destination).update();
        service.edit(scooter.id(), "Patrol", destination);
        assertThat(repository.findById(scooter.id()).orElseThrow().hubId()).isEqualTo(destination);
        jdbc.sql("UPDATE scooters SET status = 'RESERVED' WHERE id = :id").param("id", scooter.id()).update();
        assertThatThrownBy(() -> service.retire(scooter.id())).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(scooter.id()).orElseThrow().status()).isEqualTo(ScooterStatus.RESERVED);
    }

    @Test
    void zeroCapacityAndFullDestinationRejectChanges() {
        UUID destination = UUID.randomUUID();
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (:id, 'Closed hub', 0)").param("id", destination).update();
        assertThatThrownBy(() -> service.add("Standard", destination)).isInstanceOf(IllegalStateException.class);
        Scooter scooter = service.add("Standard", hub);
        assertThatThrownBy(() -> service.edit(scooter.id(), "Patrol", destination))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(scooter.id()).orElseThrow()).isEqualTo(scooter);
    }

    @Test
    void listReadsPersistedFleetIncludingRetiredScooters() {
        assertThat(repository.findAll()).isEmpty();
        Scooter active = service.add("Standard", hub);
        Scooter retired = service.retire(service.add("Patrol", hub).id());
        assertThat(service.list()).containsExactlyInAnyOrder(active, retired);
    }

    @ParameterizedTest
    @CsvSource({"-90, -180", "90, 180", "0, 0"})
    void hubPositionAcceptsBoundaryCoordinates(double lat, double lon) {
        jdbc.sql("UPDATE hubs SET lat = :lat, lon = :lon WHERE id = :id")
                .param("lat", lat).param("lon", lon).param("id", hub).update();
        assertThat(jdbc.sql("SELECT lat FROM hubs WHERE id = :id").param("id", hub)
                .query(Double.class).single()).isEqualTo(lat);
        assertThat(jdbc.sql("SELECT lon FROM hubs WHERE id = :id").param("id", hub)
                .query(Double.class).single()).isEqualTo(lon);
    }

    @ParameterizedTest
    @CsvSource(value = {"-90.01, 0", "90.01, 0", "0, -180.01", "0, 180.01", "NULL, 0", "0, NULL"}, nullValues = "NULL")
    void hubPositionRejectsInvalidOrIncompleteCoordinates(Double lat, Double lon) {
        assertThatThrownBy(() -> jdbc.sql("UPDATE hubs SET lat = :lat, lon = :lon WHERE id = :id")
                .param("lat", lat).param("lon", lon).param("id", hub).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unrelatedHubCanBeChangedWhileAnotherHubIsLocked() throws Exception {
        UUID otherHub = UUID.randomUUID();
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (:id, 'Other hub', 1)")
                .param("id", otherHub).update();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> holder = executor.submit(() -> transactions.execute(status -> {
                repository.lockHubs(hub);
                locked.countDown();
                awaitRelease(release);
                return null;
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).as("First hub is locked").isTrue();
                Future<Scooter> addition = executor.submit(() -> service.add("Standard", otherHub));
                assertThat(addition.get(5, TimeUnit.SECONDS).hubId()).isEqualTo(otherHub);
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
        }
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Lock release timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock wait interrupted", exception);
        }
    }

    @Test
    void oppositeMovesDoNotDeadlock() throws Exception {
        UUID destination = UUID.randomUUID();
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (:id, 'Destination', 2)")
                .param("id", destination).update();
        Scooter first = service.add("Standard", hub);
        Scooter second = service.add("Patrol", destination);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Scooter> outward = executor.submit(() -> {
                awaitRelease(start);
                return service.edit(first.id(), first.type(), destination);
            });
            Future<Scooter> inward = executor.submit(() -> {
                awaitRelease(start);
                return service.edit(second.id(), second.type(), hub);
            });
            start.countDown();
            assertThat(outward.get(10, TimeUnit.SECONDS).hubId()).isEqualTo(destination);
            assertThat(inward.get(10, TimeUnit.SECONDS).hubId()).isEqualTo(hub);
        }
    }

    @Test
    void concurrentAddsCannotOverfillLastSpace() throws Exception {
        service.add("Standard", hub);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> add = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try { service.add("Standard", hub); return true; }
                catch (IllegalStateException full) { return false; }
            };
            Future<Boolean> first = executor.submit(add);
            Future<Boolean> second = executor.submit(add);
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("Both callers are ready").isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM scooters").query(Long.class).single()).isEqualTo(2);
    }
}
