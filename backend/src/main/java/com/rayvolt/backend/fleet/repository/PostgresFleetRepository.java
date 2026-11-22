package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.model.ScooterStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
public class PostgresFleetRepository implements FleetRepository {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public PostgresFleetRepository(JdbcClient jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public Optional<Scooter> findById(UUID id) {
        return jdbc.sql("SELECT id, type, hub_id, status FROM scooters WHERE id = :id")
                .param("id", id)
                .query((rs, row) -> new Scooter(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getObject("hub_id", UUID.class), ScooterStatus.valueOf(rs.getString("status"))))
                .optional();
    }

    @Override
    public Scooter save(Scooter scooter) {
        jdbc.sql("""
                INSERT INTO scooters (id, type, hub_id, status) VALUES (:id, :type, :hub, :status)
                ON CONFLICT (id) DO UPDATE SET type = EXCLUDED.type,
                    hub_id = EXCLUDED.hub_id, status = EXCLUDED.status
                """)
                .param("id", scooter.id()).param("type", scooter.type())
                .param("hub", scooter.hubId()).param("status", scooter.status().name()).update();
        return scooter;
    }

    @Override
    public boolean hasSpace(UUID hubId) {
        int capacity = jdbc.sql("SELECT capacity FROM hubs WHERE id = :id").param("id", hubId)
                .query(Integer.class).optional()
                .orElseThrow(() -> new NoSuchElementException("Hub not found"));
        long occupied = jdbc.sql("SELECT count(*) FROM scooters WHERE hub_id = :id AND status <> 'RETIRED'")
                .param("id", hubId).query(Long.class).single();
        return occupied < capacity;
    }

    @Override
    public <T> T withExclusiveAccess(Supplier<T> operation) {
        return transactions.execute(status -> {
            // Serialize fleet writes, including changes made by future booking/trip modules.
            jdbc.sql("LOCK TABLE hubs, scooters IN EXCLUSIVE MODE").update();
            return operation.get();
        });
    }
}
