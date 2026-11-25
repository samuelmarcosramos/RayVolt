package com.rayvolt.backend.fleet.repository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import com.rayvolt.backend.fleet.model.*;
import com.rayvolt.backend.fleet.service.FleetService;
import com.rayvolt.backend.fleet.support.FixedFleetClock;
import com.rayvolt.backend.common.exception.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.List;
import static com.rayvolt.backend.fleet.support.FixedFleetClock.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(FixedFleetClock.class)
class PostgresFleetRepositoryIT extends FleetRepositoryContract {
    @Autowired private FleetService service;
    @MockitoSpyBean private JdbcScooterStateHistoryRepository historyAdapter;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @Tag("PT-34")
    void historyPreservesStateTimeCauseAndRecordingOrder() {
        Scooter scooter = service.add("Standard", 1L);
        service.changeStatus(scooter.id(), ScooterStatus.RESERVED, StateChangeCause.USER);
        service.changeStatus(scooter.id(), ScooterStatus.IN_USE, StateChangeCause.USER);
        service.changeStatus(scooter.id(), ScooterStatus.MAINTENANCE, StateChangeCause.TELEMETRY);
        service.changeStatus(scooter.id(), ScooterStatus.AVAILABLE, StateChangeCause.TICKET);
        service.retire(scooter.id());
        Scooter other = service.add("Patrol", 1L);
        assertThat(service.history(other.id())).hasSize(1)
                .allSatisfy(change -> assertThat(change.scooterId()).isEqualTo(other.id()));
        List<ScooterStateChange> changes = service.history(scooter.id());
        assertThat(changes).hasSize(6);
        assertThat(changes).extracting(ScooterStateChange::id).isSorted().doesNotHaveDuplicates();
        assertThat(changes).extracting(ScooterStateChange::changedAt).containsOnly(NOW);
        assertThat(changes).extracting(ScooterStateChange::scooterId).containsOnly(scooter.id());
        assertThat(changes).extracting(ScooterStateChange::fromStatus).containsExactly(null,
                ScooterStatus.AVAILABLE, ScooterStatus.RESERVED, ScooterStatus.IN_USE,
                ScooterStatus.MAINTENANCE, ScooterStatus.AVAILABLE);
        assertThat(changes).extracting(ScooterStateChange::toStatus).containsExactly(ScooterStatus.AVAILABLE,
                ScooterStatus.RESERVED, ScooterStatus.IN_USE, ScooterStatus.MAINTENANCE,
                ScooterStatus.AVAILABLE, ScooterStatus.RETIRED);
        assertThat(changes).extracting(ScooterStateChange::cause).containsExactly(StateChangeCause.OPERATOR,
                StateChangeCause.USER, StateChangeCause.USER, StateChangeCause.TELEMETRY,
                StateChangeCause.TICKET, StateChangeCause.OPERATOR);
    }

    @Test
    @Tag("PT-34")
    void rejectedTransitionLeavesStateAndHistoryUnchanged() {
        Scooter scooter = service.add("Standard", 1L);
        service.changeStatus(scooter.id(), ScooterStatus.IN_USE, StateChangeCause.USER);
        List<ScooterStateChange> before = service.history(scooter.id());
        assertThatThrownBy(() -> service.retire(scooter.id())).isInstanceOf(ConflictException.class);
        assertThat(service.get(scooter.id()).status()).isEqualTo(ScooterStatus.IN_USE);
        assertThat(service.history(scooter.id())).containsExactlyElementsOf(before);
    }

    @Test
    @Tag("PT-34")
    void auditFailureRollsBackTheStateChange() {
        Scooter scooter = service.add("Standard", 1L);
        List<ScooterStateChange> before = service.history(scooter.id());
        doThrow(new DataIntegrityViolationException("Synthetic audit failure")).when(historyAdapter).append(any());
        assertThatThrownBy(() -> service.changeStatus(scooter.id(), ScooterStatus.RESERVED, StateChangeCause.USER))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.get(scooter.id()).status()).as("Audit failure rolls back scooter state")
                .isEqualTo(ScooterStatus.AVAILABLE);
        assertThat(service.history(scooter.id())).containsExactlyElementsOf(before);
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }
}
