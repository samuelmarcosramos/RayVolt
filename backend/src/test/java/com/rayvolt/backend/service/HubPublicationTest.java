package com.rayvolt.backend.service;

import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.HubDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Tag("PT-33")
class HubPublicationTest {
    @Autowired HubService hubs;
    @Autowired FleetService fleet;
    @Autowired TransactionTemplate transactions;
    @MockitoSpyBean StubScooterGateway gateway;

    @BeforeEach
    void resetGateway() { clearInvocations(gateway); }

    private HubDetails details(String name, double lat) {
        return new HubDetails(name, "Security", 3, lat, -8.7);
    }

    @Test
    void addPublishesAllHubsOnlyAfterCommitBeforeScooterRegistration() {
        Hub created = transactions.execute(status -> {
            Hub hub = hubs.add(details("Publication gate", 40.6));
            verify(gateway, never()).publishHubs(any());
            return hub;
        });
        fleet.add("Standard", created.getId());
        var order = inOrder(gateway);
        order.verify(gateway).publishHubs(argThat(all -> all.size() == hubs.list().size()
                && all.stream().anyMatch(h -> h.getId().equals(created.getId()))));
        order.verify(gateway).registerScooter(anyLong(), eq(created.getId()));
    }

    @Test
    void editPublishesUpdatedCoordinatesAfterCommit() {
        Hub created = hubs.add(details("Coordinate gate", 40.6));
        clearInvocations(gateway);
        transactions.executeWithoutResult(status -> {
            hubs.edit(created.getId(), details("Updated coordinate gate", 41.0));
            verify(gateway, never()).publishHubs(any());
        });
        verify(gateway).publishHubs(argThat(all -> all.stream().anyMatch(h ->
                h.getId().equals(created.getId()) && h.getLat().equals(41.0)
                        && h.getName().equals("Updated coordinate gate"))));
    }

    @Test
    void rollbackNeverPublishesAnUncommittedHub() {
        List<Long> before = hubs.list().stream().map(Hub::getId).toList();
        transactions.executeWithoutResult(status -> {
            hubs.add(details("Rolled back gate", 40.6));
            status.setRollbackOnly();
        });
        verify(gateway, never()).publishHubs(any());
        assertThat(hubs.list()).extracting(Hub::getId).containsExactlyElementsOf(before);
    }

    @Test
    void brokerFailureDoesNotUndoCommittedHub() {
        doThrow(new IllegalStateException("Synthetic broker outage")).when(gateway).publishHubs(any());
        Hub created = hubs.add(details("Offline gate", 40.6));
        assertThat(hubs.get(created.getId()).getName()).isEqualTo("Offline gate");
        verify(gateway).publishHubs(any());
    }
}
