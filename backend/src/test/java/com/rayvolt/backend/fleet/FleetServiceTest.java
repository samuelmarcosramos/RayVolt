package com.rayvolt.backend.fleet;

import com.rayvolt.backend.fleet.model.*;
import com.rayvolt.backend.fleet.repository.FleetRepository;
import com.rayvolt.backend.fleet.service.FleetService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-32")
class FleetServiceTest {
    private final UUID hub = UUID.randomUUID();
    private final TestRepository repository = new TestRepository();
    private final FleetService service = new FleetService(repository);

    @Test
    void addedScooterStartsAvailable() {
        Scooter scooter = service.add("Standard", hub);
        assertThat(scooter.status()).as("New scooters start available").isEqualTo(ScooterStatus.AVAILABLE);
        assertThat(repository.items.get(scooter.id())).isEqualTo(scooter);
    }

    @Test
    void fullHubRejectsAdditionAndMoveButAllowsTypeChange() {
        Scooter scooter = service.add("Standard", hub);
        repository.space = false;
        assertThatThrownBy(() -> service.add("Standard", hub)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.edit(scooter.id(), "Patrol", UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThat(service.edit(scooter.id(), "Patrol", hub).type()).isEqualTo("Patrol");
        assertThat(repository.items).hasSize(1);
    }

    @Test
    void canMoveToHubWithSpace() {
        Scooter scooter = service.add("Standard", hub);
        UUID destination = UUID.randomUUID();
        assertThat(service.edit(scooter.id(), "Patrol", destination).hubId()).isEqualTo(destination);
    }

    @ParameterizedTest
    @EnumSource(value = ScooterStatus.class, names = {"RESERVED", "IN_USE", "RETIRED"})
    void protectedStatesRejectChanges(ScooterStatus status) {
        Scooter scooter = new Scooter(UUID.randomUUID(), "Standard", hub, status);
        repository.save(scooter);
        assertThatThrownBy(() -> service.edit(scooter.id(), "Patrol", hub)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.retire(scooter.id())).isInstanceOf(IllegalStateException.class);
        assertThat(repository.items.get(scooter.id())).as("Rejected changes preserve the scooter").isEqualTo(scooter);
    }

    @Test
    void retirementKeepsIdentityAndHidesScooter() {
        Scooter original = service.add("Standard", hub);
        Scooter retired = service.retire(original.id());
        assertThat(retired.status()).isEqualTo(ScooterStatus.RETIRED);
        assertThat(retired.visibleToUsers()).as("Retired scooters are hidden from users").isFalse();
        assertThat(repository.items).containsKey(original.id());
    }

    @Test
    void missingScooterAndBlankTypeAreRejected() {
        assertThatThrownBy(() -> service.retire(UUID.randomUUID())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.add(" ", hub)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.items).isEmpty();
    }

    private static class TestRepository implements FleetRepository {
        private final Map<UUID, Scooter> items = new HashMap<>();
        private boolean space = true;
        public Optional<Scooter> findById(UUID id) { return Optional.ofNullable(items.get(id)); }
        public Scooter save(Scooter scooter) { items.put(scooter.id(), scooter); return scooter; }
        public boolean hasSpace(UUID hubId) { return space; }
        public <T> T withExclusiveAccess(Supplier<T> operation) { return operation.get(); }
    }
}
