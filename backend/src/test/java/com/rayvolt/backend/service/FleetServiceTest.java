package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.BadRequestException;
import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("PT-32")
class FleetServiceTest {
    private final Long hub = 1L;
    private final Long destination = 2L;
    private final ScooterRepository scooters = mock(ScooterRepository.class);
    private final HubRepository hubs = mock(HubRepository.class);
    private final FleetService service = new FleetService(scooters, hubs);

    @BeforeEach
    void setUp() {
        when(hubs.findByIdForUpdate(hub)).thenReturn(Optional.of(new Hub(hub, "Main Gate", 2, null, null)));
        when(hubs.findByIdForUpdate(destination)).thenReturn(Optional.of(new Hub(destination, "Warehouse", 2, null, null)));
        when(scooters.save(any())).thenAnswer(call -> {
            Scooter scooter = call.getArgument(0);
            return scooter.getId() != null ? scooter
                    : new Scooter(7L, scooter.getType(), scooter.getHubId(), scooter.getStatus());
        });
    }

    private Scooter stored(ScooterStatus status) {
        Scooter scooter = new Scooter(7L, "Standard", hub, status);
        when(scooters.findByIdForUpdate(7L)).thenReturn(Optional.of(scooter));
        return scooter;
    }

    private void occupied(Long hubId, long count) {
        when(scooters.countByHubIdAndStatusNot(hubId, ScooterStatus.RETIRED)).thenReturn(count);
    }

    @Test
    void addedScooterStartsAvailable() {
        Scooter scooter = service.add("Standard", hub);
        assertThat(scooter.getStatus()).as("New scooters start available").isEqualTo(ScooterStatus.AVAILABLE);
        verify(scooters).save(any());
    }

    @Test
    void getReturnsStoredScooterAndRejectsUnknownIds() {
        Scooter scooter = new Scooter(7L, "Standard", hub, ScooterStatus.AVAILABLE);
        when(scooters.findById(7L)).thenReturn(Optional.of(scooter));
        assertThat(service.get(7L)).isSameAs(scooter);
        assertThatThrownBy(() -> service.get(999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void fullHubRejectsAddition() {
        occupied(hub, 2);
        assertThatThrownBy(() -> service.add("Standard", hub)).isInstanceOf(ConflictException.class);
        verify(scooters, never()).save(any());
    }

    @Test
    void hubWithOneFreeSpaceAcceptsAddition() {
        occupied(hub, 1);
        assertThat(service.add("Standard", hub).getHubId()).isEqualTo(hub);
    }

    @Test
    void fullDestinationRejectsMoveButAllowsTypeChange() {
        Scooter scooter = stored(ScooterStatus.AVAILABLE);
        occupied(destination, 2);
        assertThatThrownBy(() -> service.edit(7L, "Patrol", destination)).isInstanceOf(ConflictException.class);
        assertThat(scooter.getHubId()).as("Rejected move keeps the old hub").isEqualTo(hub);
        assertThat(service.edit(7L, "Patrol", hub).getType()).isEqualTo("Patrol");
    }

    @Test
    void canMoveToHubWithSpace() {
        stored(ScooterStatus.AVAILABLE);
        assertThat(service.edit(7L, "Patrol", destination).getHubId()).isEqualTo(destination);
    }

    @Test
    void unknownHubIsRejected() {
        assertThatThrownBy(() -> service.add("Standard", 999L)).isInstanceOf(NotFoundException.class);
        verify(scooters, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ScooterStatus.class, names = {"RESERVED", "IN_USE", "RETIRED"})
    void protectedStatesRejectChanges(ScooterStatus status) {
        Scooter scooter = stored(status);
        assertThatThrownBy(() -> service.edit(7L, "Patrol", hub)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.retire(7L)).isInstanceOf(ConflictException.class);
        assertThat(scooter.getType()).as("Rejected changes keep the type").isEqualTo("Standard");
        assertThat(scooter.getStatus()).as("Rejected changes keep the status").isEqualTo(status);
        verify(scooters, never()).save(any());
    }

    @Test
    void retirementKeepsIdentityAndHidesScooter() {
        stored(ScooterStatus.AVAILABLE);
        Scooter retired = service.retire(7L);
        assertThat(retired.getId()).isEqualTo(7L);
        assertThat(retired.getStatus()).isEqualTo(ScooterStatus.RETIRED);
        assertThat(retired.visibleToUsers()).as("Retired scooters are hidden from users").isFalse();
    }

    @Test
    void missingScooterAndBlankTypeAreRejected() {
        assertThatThrownBy(() -> service.retire(999L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.add(" ", hub)).isInstanceOf(BadRequestException.class);
        verify(scooters, never()).save(any());
    }
}
