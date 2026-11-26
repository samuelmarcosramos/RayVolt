package com.rayvolt.backend.service;

import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@Tag("PT-46")
class FleetSyncServiceTest {
    private final ScooterRepository scooters = mock(ScooterRepository.class);
    private final HubRepository hubs = mock(HubRepository.class);
    private final ScooterGateway gateway = mock(ScooterGateway.class);
    private final FleetSyncService sync = new FleetSyncService(scooters, hubs, gateway);
    private final List<Hub> seeded = List.of(new Hub(1L, "Main Gate", 15, 40.612, -8.7175));

    @Test
    void publishesHubsThenActiveAndRetiredScooters() {
        when(hubs.findAll(Sort.by("id"))).thenReturn(seeded);
        when(scooters.findAllByOrderByIdAsc()).thenReturn(List.of(
                new Scooter(1L, "Standard", 1L, ScooterStatus.AVAILABLE),
                new Scooter(2L, "Patrol", 1L, ScooterStatus.RETIRED)));
        sync.publishAll();
        var order = inOrder(gateway);
        order.verify(gateway).onConnected(any());
        order.verify(gateway).publishHubs(seeded);
        order.verify(gateway).registerScooter(1L, 1L);
        order.verify(gateway).retireScooter(2L, 1L);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void publishesEverythingEachTimeTheGatewayConnects() {
        when(hubs.findAll(Sort.by("id"))).thenReturn(seeded);
        ArgumentCaptor<Runnable> onConnected = ArgumentCaptor.forClass(Runnable.class);
        verify(gateway).onConnected(onConnected.capture());
        onConnected.getValue().run();
        onConnected.getValue().run();
        verify(gateway, times(2)).publishHubs(seeded);
    }

    @Test
    void aFailedPublishIsLoggedAndNotThrown() {
        when(hubs.findAll(Sort.by("id"))).thenReturn(seeded);
        doThrow(new IllegalStateException("Broker down")).when(gateway).publishHubs(anyList());
        assertThatCode(sync::publishAll).doesNotThrowAnyException();
        verify(gateway, never()).registerScooter(anyLong(), anyLong());
    }
}
