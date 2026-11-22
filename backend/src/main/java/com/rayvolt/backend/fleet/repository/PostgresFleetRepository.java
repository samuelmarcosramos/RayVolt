package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.model.ScooterStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Arrays;
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
    public List<Scooter> findAll() {
        return jdbc.sql("SELECT id, type, hub_id, status FROM scooters ORDER BY id")
                .query(PostgresFleetRepository::mapScooter).list();
    }

    @Override
    public Optional<Scooter> findById(UUID id) {
        return find(id, false);
    }

    @Override
    public Optional<Scooter> findByIdForUpdate(UUID id) {
        return find(id, true);
    }

    private Optional<Scooter> find(UUID id, boolean forUpdate) {
        String sql = "SELECT id, type, hub_id, status FROM scooters WHERE id = :id";
        return jdbc.sql(sql + (forUpdate ? " FOR UPDATE" : ""))
                .param("id", id)
                .query(PostgresFleetRepository::mapScooter)
                .optional();
    }

    private static Scooter mapScooter(ResultSet rs, int row) throws SQLException {
        return new Scooter(rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getObject("hub_id", UUID.class), ScooterStatus.valueOf(rs.getString("status")));
    }

    @Override
    public void lockHubs(UUID... hubIds) {
        for (UUID id : Arrays.stream(hubIds).distinct().sorted().toList()) {
            jdbc.sql("SELECT id FROM hubs WHERE id = :id FOR UPDATE").param("id", id)
                    .query(UUID.class).optional()
                    .orElseThrow(() -> new NoSuchElementException("Hub not found"));
        }
    }

    @Override
    public Scooter save(Scooter scooter) {
        int updated = jdbc.sql("UPDATE scooters SET type = :type, hub_id = :hub, status = :status WHERE id = :id")
                .param("id", scooter.id()).param("type", scooter.type())
                .param("hub", scooter.hubId()).param("status", scooter.status().name()).update();
        if (updated == 0) {
            jdbc.sql("INSERT INTO scooters (id, type, hub_id, status) VALUES (:id, :type, :hub, :status)")
                    .param("id", scooter.id()).param("type", scooter.type())
                    .param("hub", scooter.hubId()).param("status", scooter.status().name()).update();
        }
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
    public <T> T withTransaction(Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }
}
