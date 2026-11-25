package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.PositionStatus;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterLive;
import com.rayvolt.backend.model.ScooterState;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.model.TelemetryReading;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterRepository;
import com.rayvolt.backend.repository.ScooterStateRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("PT-46")
class SseServiceTest {
    private static final Instant RECEIVED = Instant.parse("2026-11-24T10:00:01Z");
    private final ScooterRepository scooters = mock(ScooterRepository.class);
    private final ScooterStateRepository states = mock(ScooterStateRepository.class);
    private final HubRepository hubs = mock(HubRepository.class);
    private final SseService service = new SseService(scooters, states, hubs);
    private final Hub mainGate = new Hub(1L, "Main Gate", 15, 40.612, -8.7175);

    private ScooterState stateOf(long scooterId) {
        return new ScooterState(new TelemetryReading(scooterId, Instant.parse("2026-11-24T10:00:00Z"), 87, true, 0,
                1L, 40.612, -8.7175, PositionStatus.AT_HUB, false), RECEIVED);
    }

    @Test
    void listJoinsEachScooterWithItsLatestTelemetryAndLeavesRetiredOut() {
        when(scooters.findAllByOrderByIdAsc()).thenReturn(List.of(
                new Scooter(1L, "Standard", 1L, ScooterStatus.AVAILABLE),
                new Scooter(2L, "Cargo", 1L, ScooterStatus.MAINTENANCE),
                new Scooter(3L, "Patrol", 1L, ScooterStatus.RETIRED)));
        when(states.findAll()).thenReturn(List.of(stateOf(1L), stateOf(3L)));
        when(hubs.findAll()).thenReturn(List.of(mainGate));
        assertThat(service.list()).containsExactly(
                new ScooterLive(1L, ScooterStatus.AVAILABLE, 87, 40.612, -8.7175, PositionStatus.AT_HUB, "Main Gate",
                        false, RECEIVED),
                new ScooterLive(2L, ScooterStatus.MAINTENANCE, null, null, null, null, null, false, null));
    }

    @Test
    void getReturnsOneScooter() {
        when(scooters.findById(1L)).thenReturn(Optional.of(new Scooter(1L, "Standard", 1L, ScooterStatus.IN_USE)));
        when(states.findById(1L)).thenReturn(Optional.of(stateOf(1L)));
        when(hubs.findById(1L)).thenReturn(Optional.of(mainGate));
        assertThat(service.get(1L).battery()).isEqualTo(87);
        assertThat(service.get(1L).hubName()).isEqualTo("Main Gate");
        assertThat(service.get(1L).status()).isEqualTo(ScooterStatus.IN_USE);
    }

    @Test
    void getOfUnknownOrRetiredScooterIsNotFound() {
        when(scooters.findById(8L)).thenReturn(Optional.empty());
        when(scooters.findById(3L)).thenReturn(Optional.of(new Scooter(3L, "Patrol", 1L, ScooterStatus.RETIRED)));
        assertThatThrownBy(() -> service.get(8L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(3L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void finishedListenerIsForgotten() {
        service.subscribe().complete();
        service.publish(new Scooter(1L, "Standard", 1L, ScooterStatus.AVAILABLE), null);
        assertThat(service.listenerCount()).isZero();
    }

    @Test
    void scooterOutsideAHubHasNoHubName() {
        ScooterState moving = new ScooterState(new TelemetryReading(1L, Instant.parse("2026-11-24T10:00:00Z"), 87,
                false, 15, null, 40.61, -8.716, PositionStatus.MOVING, false), RECEIVED);
        when(scooters.findById(1L)).thenReturn(Optional.of(new Scooter(1L, "Standard", 1L, ScooterStatus.IN_USE)));
        when(states.findById(1L)).thenReturn(Optional.of(moving));
        assertThat(service.get(1L).hubName()).isNull();
        verifyNoInteractions(hubs);
    }

    @Test
    void shutdownClosesEveryOpenStream() {
        service.subscribe();
        service.subscribe();
        service.closeAll();
        assertThat(service.listenerCount()).isZero();
    }
}
