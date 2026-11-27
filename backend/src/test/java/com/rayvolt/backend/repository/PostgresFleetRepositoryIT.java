package com.rayvolt.backend.repository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.service.FleetService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.test.context.bean.override.convention.TestBean;
import com.rayvolt.backend.errors.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class PostgresFleetRepositoryIT extends FleetRepositoryContract {
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    @TestBean private Clock clock;
    static Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    @Autowired private FleetService service;
    @MockitoSpyBean private ScooterStateHistoryRepository historyAdapter;

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
        service.changeStatus(scooter.getId(), ScooterStatus.RESERVED, StateChangeCause.USER);
        service.changeStatus(scooter.getId(), ScooterStatus.IN_USE, StateChangeCause.USER);
        service.changeStatus(scooter.getId(), ScooterStatus.MAINTENANCE, StateChangeCause.TELEMETRY);
        service.changeStatus(scooter.getId(), ScooterStatus.AVAILABLE, StateChangeCause.TICKET);
        service.retire(scooter.getId());
        Scooter other = service.add("Patrol", 1L);
        assertThat(service.history(other.getId())).hasSize(1)
                .allSatisfy(change -> assertThat(change.getScooterId()).isEqualTo(other.getId()));
        List<ScooterStateChange> changes = service.history(scooter.getId());
        assertThat(changes).hasSize(6);
        assertThat(changes).extracting(ScooterStateChange::getId).isSorted().doesNotHaveDuplicates();
        assertThat(changes).extracting(ScooterStateChange::getChangedAt).containsOnly(NOW);
        assertThat(changes).extracting(ScooterStateChange::getScooterId).containsOnly(scooter.getId());
        assertThat(changes).extracting(ScooterStateChange::getFromStatus).containsExactly(null,
                ScooterStatus.AVAILABLE, ScooterStatus.RESERVED, ScooterStatus.IN_USE,
                ScooterStatus.MAINTENANCE, ScooterStatus.AVAILABLE);
        assertThat(changes).extracting(ScooterStateChange::getToStatus).containsExactly(ScooterStatus.AVAILABLE,
                ScooterStatus.RESERVED, ScooterStatus.IN_USE, ScooterStatus.MAINTENANCE,
                ScooterStatus.AVAILABLE, ScooterStatus.RETIRED);
        assertThat(changes).extracting(ScooterStateChange::getCause).containsExactly(StateChangeCause.OPERATOR,
                StateChangeCause.USER, StateChangeCause.USER, StateChangeCause.TELEMETRY,
                StateChangeCause.TICKET, StateChangeCause.OPERATOR);
    }

    @Test
    @Tag("PT-34")
    void rejectedTransitionLeavesStateAndHistoryUnchanged() {
        Scooter scooter = service.add("Standard", 1L);
        service.changeStatus(scooter.getId(), ScooterStatus.IN_USE, StateChangeCause.USER);
        List<ScooterStateChange> before = service.history(scooter.getId());
        assertThatThrownBy(() -> service.retire(scooter.getId())).isInstanceOf(ConflictException.class);
        assertThat(service.get(scooter.getId()).getStatus()).isEqualTo(ScooterStatus.IN_USE);
        assertThat(service.history(scooter.getId())).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(before);
    }

    @Test
    @Tag("PT-34")
    void auditFailureRollsBackTheStateChange() {
        Scooter scooter = service.add("Standard", 1L);
        List<ScooterStateChange> before = service.history(scooter.getId());
        doThrow(new DataIntegrityViolationException("Synthetic audit failure")).when(historyAdapter).save(any());
        assertThatThrownBy(() -> service.changeStatus(scooter.getId(), ScooterStatus.RESERVED, StateChangeCause.USER))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.get(scooter.getId()).getStatus()).as("Audit failure rolls back scooter state")
                .isEqualTo(ScooterStatus.AVAILABLE);
        assertThat(service.history(scooter.getId())).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(before);
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }
}
