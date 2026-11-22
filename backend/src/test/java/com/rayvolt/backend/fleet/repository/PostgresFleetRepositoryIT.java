package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.*;
import com.rayvolt.backend.fleet.service.FleetService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Tag("PT-32")
class PostgresFleetRepositoryIT {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private FleetService service;
    @Autowired private PostgresFleetRepository repository;
    @Autowired private JdbcClient jdbc;
    private UUID hub;

    @BeforeEach
    void prepareHub() {
        jdbc.sql("DELETE FROM scooters").update();
        jdbc.sql("DELETE FROM hubs").update();
        hub = UUID.randomUUID();
        jdbc.sql("INSERT INTO hubs VALUES (:id, 'Synthetic hub', 2)").param("id", hub).update();
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

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
        assertThatThrownBy(() -> repository.withExclusiveAccess(() -> {
            repository.save(scooter);
            throw new IllegalStateException("Synthetic failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(scooter.id())).isEmpty();
    }

    @Test
    void movesPersistAndReservedScootersCannotRetire() {
        Scooter scooter = service.add("Standard", hub);
        UUID destination = UUID.randomUUID();
        jdbc.sql("INSERT INTO hubs VALUES (:id, 'Destination', 1)").param("id", destination).update();
        service.edit(scooter.id(), "Patrol", destination);
        assertThat(repository.findById(scooter.id()).orElseThrow().hubId()).isEqualTo(destination);
        jdbc.sql("UPDATE scooters SET status = 'RESERVED' WHERE id = :id").param("id", scooter.id()).update();
        assertThatThrownBy(() -> service.retire(scooter.id())).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(scooter.id()).orElseThrow().status()).isEqualTo(ScooterStatus.RESERVED);
    }

    @Test
    void zeroCapacityAndFullDestinationRejectChanges() {
        UUID destination = UUID.randomUUID();
        jdbc.sql("INSERT INTO hubs VALUES (:id, 'Closed hub', 0)").param("id", destination).update();
        assertThatThrownBy(() -> service.add("Standard", destination)).isInstanceOf(IllegalStateException.class);
        Scooter scooter = service.add("Standard", hub);
        assertThatThrownBy(() -> service.edit(scooter.id(), "Patrol", destination))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(scooter.id()).orElseThrow()).isEqualTo(scooter);
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
