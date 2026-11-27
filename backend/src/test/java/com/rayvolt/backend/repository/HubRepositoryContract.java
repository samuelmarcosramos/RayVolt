package com.rayvolt.backend.repository;

import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Tag("PT-33")
abstract class HubRepositoryContract {
    @Autowired HubService hubs;
    @Autowired FleetService fleet;
    @Autowired ScooterRepository scooters;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM scooter_state_history").update();
        jdbc.sql("DELETE FROM scooter_state").update();
        jdbc.sql("DELETE FROM scooters").update();
        jdbc.sql("DELETE FROM hubs").update();
    }

    private HubDetails details(String name, int capacity) { return new HubDetails(name, "Security", capacity, 40.6, -8.7); }

    @Test
    void createdAndEditedHubPersistsAndAppearsInUserList() {
        Hub hub = hubs.add(details("North Gate", 3));
        assertThat(hubs.get(hub.getId()).getDepartment()).isEqualTo("Security");
        fleet.add("Standard", hub.getId());
        fleet.add("Patrol", hub.getId());
        assertThatThrownBy(() -> hubs.edit(hub.getId(), details("Changed", 1))).isInstanceOf(ConflictException.class);
        assertThat(hubs.get(hub.getId()).getName()).isEqualTo("North Gate");
        hubs.edit(hub.getId(), new HubDetails("South Gate", "Logistics", 2, -90.0, 180.0));
        assertThat(hubs.list()).singleElement().satisfies(saved -> {
            assertThat(saved.getName()).isEqualTo("South Gate");
            assertThat(saved.getDepartment()).isEqualTo("Logistics");
            assertThat(saved.getCapacity()).isEqualTo(2);
            assertThat(saved.getLat()).isEqualTo(-90.0);
            assertThat(saved.getLon()).isEqualTo(180.0);
        });
    }

    @Test
    void concurrentCreationCannotDuplicateNames() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> add = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try { hubs.add(details("Gate", 2)); return true; }
                catch (ConflictException exception) { return false; }
            };
            Future<Boolean> first = executor.submit(add);
            Future<Boolean> second = executor.submit(add);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(hubs.list()).hasSize(1);
    }

    @Test
    void concurrentArrivalAndCapacityReductionCannotOverfillHub() throws Exception {
        Hub hub = hubs.add(details("Gate", 2));
        fleet.add("Standard", hub.getId());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> arrival = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try { fleet.add("Patrol", hub.getId()); return true; }
                catch (ConflictException exception) { return false; }
            });
            Future<Boolean> reduction = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try { hubs.edit(hub.getId(), details("Gate", 1)); return true; }
                catch (ConflictException exception) { return false; }
            });
            start.countDown();
            assertThat(List.of(arrival.get(10, TimeUnit.SECONDS), reduction.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(scooters.countByHubIdAndStatusNot(hub.getId(), ScooterStatus.RETIRED))
                .isLessThanOrEqualTo(hubs.get(hub.getId()).getCapacity());
    }
}
