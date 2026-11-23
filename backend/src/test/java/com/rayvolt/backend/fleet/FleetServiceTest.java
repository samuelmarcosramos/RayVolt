package com.rayvolt.backend.fleet;

import com.rayvolt.backend.fleet.model.*;
import com.rayvolt.backend.fleet.repository.FleetRepository;
import com.rayvolt.backend.fleet.service.FleetService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import com.rayvolt.backend.common.exception.*;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-32")
class FleetServiceTest {
    private final Long hub = 1L;
    private final TestRepository repository = new TestRepository();
    private final FleetService service = new FleetService(repository);

    @Test
    void addedScooterStartsAvailable() {
        Scooter scooter = service.add("Standard", hub);
        assertThat(scooter.status()).as("New scooters start available").isEqualTo(ScooterStatus.AVAILABLE);
        assertThat(repository.items.get(scooter.id())).isEqualTo(scooter);
    }

    @Test
    void getReturnsPersistedScooterAndRejectsUnknownIds() {
        Scooter scooter = service.add("Standard", hub);
        assertThat(service.get(scooter.id())).isEqualTo(scooter);
        assertThatThrownBy(() -> service.get(999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void fullHubRejectsAdditionAndMoveButAllowsTypeChange() {
        Scooter scooter = service.add("Standard", hub);
        repository.space = false;
        assertThatThrownBy(() -> service.add("Standard", hub)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.edit(scooter.id(), "Patrol", 999L)).isInstanceOf(ConflictException.class);
        assertThat(service.edit(scooter.id(), "Patrol", hub).type()).isEqualTo("Patrol");
        assertThat(repository.items).hasSize(1);
    }

    @Test
    void canMoveToHubWithSpace() {
        Scooter scooter = service.add("Standard", hub);
        Long destination = 2L;
        assertThat(service.edit(scooter.id(), "Patrol", destination).hubId()).isEqualTo(destination);
    }

    @ParameterizedTest
    @EnumSource(value = ScooterStatus.class, names = {"RESERVED", "IN_USE", "RETIRED"})
    void protectedStatesRejectChanges(ScooterStatus status) {
        Scooter scooter = new Scooter(999L, "Standard", hub, status);
        repository.save(scooter);
        assertThatThrownBy(() -> service.edit(scooter.id(), "Patrol", hub)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.retire(scooter.id())).isInstanceOf(ConflictException.class);
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
        assertThatThrownBy(() -> service.retire(999L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.add(" ", hub)).isInstanceOf(BadRequestException.class);
        assertThat(repository.items).isEmpty();
    }

    private static class TestRepository implements FleetRepository {
        private final Map<Long, Scooter> items = new HashMap<>();
        private boolean space = true;
        public List<Scooter> findAll() { return List.copyOf(items.values()); }
        public Optional<Scooter> findById(Long id) { return Optional.ofNullable(items.get(id)); }
        public Optional<Scooter> findByIdForUpdate(Long id) { return findById(id); }
        public void lockHubs(Long... hubIds) { }
        private long nextId = 1;
        public Scooter save(Scooter scooter) {
            Scooter saved = scooter.id() == null
                    ? new Scooter(nextId++, scooter.type(), scooter.hubId(), scooter.status()) : scooter;
            items.put(saved.id(), saved);
            return saved;
        }
        public boolean hasSpace(Long hubId) { return space; }
    }
}
