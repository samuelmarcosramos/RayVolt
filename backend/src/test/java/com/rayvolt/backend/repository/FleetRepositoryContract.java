package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.*;
import com.rayvolt.backend.service.FleetService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import com.rayvolt.backend.errors.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Tag("PT-32")
abstract class FleetRepositoryContract {
    @Autowired private FleetService service;
    @Autowired private ScooterRepository scooters;
    @Autowired private HubRepository hubs;
    @Autowired private JdbcClient jdbc;
    @Autowired private TransactionTemplate transactions;
    private Long hub;

    @BeforeEach
    void prepareHub() {
        jdbc.sql("DELETE FROM scooters").update();
        jdbc.sql("DELETE FROM hubs").update();
        hub = 1L;
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (:id, 'Synthetic hub', 2)").param("id", hub).update();
    }

    @Test
    void capacityBoundariesAndRetirementPersist() {
        Scooter first = service.add("Standard", hub);
        service.add("Patrol", hub);
        assertThatThrownBy(() -> service.add("Standard", hub)).as("Exactly full has no room")
                .isInstanceOf(ConflictException.class);
        service.retire(first.getId());
        assertThat(scooters.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(ScooterStatus.RETIRED);
        assertThat(service.add("Standard", hub)).as("Retirement releases capacity").isNotNull();
        assertThat(jdbc.sql("SELECT count(*) FROM scooters").query(Long.class).single()).isEqualTo(3);
    }

    @Test
    void unknownHubIsRejectedAndFailedOperationRollsBack() {
        assertThatThrownBy(() -> service.add("Standard", 999L)).isInstanceOf(NotFoundException.class);
        Scooter scooter = new Scooter("Standard", hub);
        assertThatThrownBy(() -> transactions.execute(status -> {
            hubs.findByIdForUpdate(hub);
            scooters.save(scooter);
            throw new IllegalStateException("Synthetic failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(scooters.findAll()).as("Failed transaction leaves no scooter behind").isEmpty();
    }

    @Test
    void movesPersistAndReservedScootersCannotRetire() {
        Scooter scooter = service.add("Standard", hub);
        Long destination = 2L;
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (:id, 'Destination', 1)").param("id", destination).update();
        service.edit(scooter.getId(), "Patrol", destination);
        assertThat(scooters.findById(scooter.getId()).orElseThrow().getHubId()).isEqualTo(destination);
        jdbc.sql("UPDATE scooters SET status = 'RESERVED' WHERE id = :id").param("id", scooter.getId()).update();
        assertThatThrownBy(() -> service.retire(scooter.getId())).isInstanceOf(ConflictException.class);
        assertThat(scooters.findById(scooter.getId()).orElseThrow().getStatus()).isEqualTo(ScooterStatus.RESERVED);
    }

    @Test
    void zeroCapacityAndFullDestinationRejectChanges() {
        Long destination = 2L;
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (:id, 'Closed hub', 0)").param("id", destination).update();
        assertThatThrownBy(() -> service.add("Standard", destination)).isInstanceOf(ConflictException.class);
        Scooter scooter = service.add("Standard", hub);
        assertThatThrownBy(() -> service.edit(scooter.getId(), "Patrol", destination))
                .isInstanceOf(ConflictException.class);
        assertThat(scooters.findById(scooter.getId()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(scooter);
    }

    @Test
    void listReadsPersistedFleetIncludingRetiredScooters() {
        assertThat(scooters.findAll()).isEmpty();
        Scooter active = service.add("Standard", hub);
        Scooter retired = service.retire(service.add("Patrol", hub).getId());
        assertThat(service.list()).usingRecursiveFieldByFieldElementComparator().containsExactly(active, retired);
        assertThat(service.get(active.getId())).usingRecursiveComparison().isEqualTo(active);
        assertThat(service.get(retired.getId())).usingRecursiveComparison().isEqualTo(retired);
        assertThatThrownBy(() -> service.get(999L)).isInstanceOf(NotFoundException.class);
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
        Long otherHub = 2L;
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (:id, 'Other hub', 1)")
                .param("id", otherHub).update();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> holder = executor.submit(() -> transactions.execute(status -> {
                hubs.findByIdForUpdate(hub);
                locked.countDown();
                awaitRelease(release);
                return null;
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).as("First hub is locked").isTrue();
                Future<Scooter> addition = executor.submit(() -> service.add("Standard", otherHub));
                assertThat(addition.get(5, TimeUnit.SECONDS).getHubId()).isEqualTo(otherHub);
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
        Long destination = 2L;
        jdbc.sql("INSERT INTO hubs (id, name, capacity) VALUES (:id, 'Destination', 2)")
                .param("id", destination).update();
        Scooter first = service.add("Standard", hub);
        Scooter second = service.add("Patrol", destination);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Scooter> outward = executor.submit(() -> {
                awaitRelease(start);
                return service.edit(first.getId(), first.getType(), destination);
            });
            Future<Scooter> inward = executor.submit(() -> {
                awaitRelease(start);
                return service.edit(second.getId(), second.getType(), hub);
            });
            start.countDown();
            assertThat(outward.get(10, TimeUnit.SECONDS).getHubId()).isEqualTo(destination);
            assertThat(inward.get(10, TimeUnit.SECONDS).getHubId()).isEqualTo(hub);
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
                catch (ConflictException full) { return false; }
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
