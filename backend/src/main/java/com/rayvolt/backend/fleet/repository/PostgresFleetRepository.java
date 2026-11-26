package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.model.ScooterStatus;
import com.rayvolt.backend.fleet.model.Hub;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Arrays;
import com.rayvolt.backend.common.exception.NotFoundException;
import java.util.Optional;
import java.util.Objects;
import org.springframework.jdbc.support.GeneratedKeyHolder;

@Repository
public class PostgresFleetRepository implements FleetRepository {
    private final JdbcClient jdbc;

    public PostgresFleetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Scooter> findAll() {
        return jdbc.sql("SELECT id, type, hub_id, status, battery_percent FROM scooters ORDER BY id")
                .query(PostgresFleetRepository::mapScooter).list();
    }

    @Override
    public List<Scooter> findAvailable(Long hubId, String type, int minimumBatteryPercent) {
        String sql = "SELECT id, type, hub_id, status, battery_percent FROM scooters "
                + "WHERE hub_id = :hub AND status = 'AVAILABLE' AND battery_percent >= :minimumBattery";
        if (type != null) {
            sql += " AND LOWER(type) = LOWER(:type)";
        }
        var query = jdbc.sql(sql + " ORDER BY id")
                .param("hub", hubId).param("minimumBattery", minimumBatteryPercent);
        if (type != null) {
            query.param("type", type);
        }
        return query.query(PostgresFleetRepository::mapScooter).list();
    }

    @Override
    public List<Hub> findHubs() {
        return jdbc.sql("SELECT id, name FROM hubs ORDER BY name, id")
                .query((rs, row) -> new Hub(rs.getLong("id"), rs.getString("name"))).list();
    }

    @Override
    public Optional<Scooter> findById(Long id) {
        return find(id, false);
    }

    @Override
    public Optional<Scooter> findByIdForUpdate(Long id) {
        return find(id, true);
    }

    private Optional<Scooter> find(Long id, boolean forUpdate) {
        String sql = "SELECT id, type, hub_id, status, battery_percent FROM scooters WHERE id = :id";
        return jdbc.sql(sql + (forUpdate ? " FOR UPDATE" : ""))
                .param("id", id)
                .query(PostgresFleetRepository::mapScooter)
                .optional();
    }

    private static Scooter mapScooter(ResultSet rs, int row) throws SQLException {
        return new Scooter(rs.getLong("id"), rs.getString("type"),
                rs.getLong("hub_id"), ScooterStatus.valueOf(rs.getString("status")),
                rs.getInt("battery_percent"));
    }

    @Override
    public void lockHubs(Long... hubIds) {
        for (Long id : Arrays.stream(hubIds).distinct().sorted().toList()) {
            jdbc.sql("SELECT id FROM hubs WHERE id = :id FOR UPDATE").param("id", id)
                    .query(Long.class).optional()
                    .orElseThrow(() -> new NotFoundException("Hub not found"));
        }
    }

    @Override
    public Scooter save(Scooter scooter) {
        if (scooter.id() == null) {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.sql("INSERT INTO scooters (type, hub_id, status, battery_percent) "
                            + "VALUES (:type, :hub, :status, :battery)")
                    .param("type", scooter.type()).param("hub", scooter.hubId())
                    .param("status", scooter.status().name()).param("battery", scooter.batteryPercent())
                    .update(keys, "id");
            long id = Objects.requireNonNull(keys.getKey(), "Database did not return a scooter ID").longValue();
            return new Scooter(id, scooter.type(), scooter.hubId(), scooter.status(), scooter.batteryPercent());
        }
        int updated = jdbc.sql("UPDATE scooters SET type = :type, hub_id = :hub, status = :status, "
                        + "battery_percent = :battery WHERE id = :id")
                .param("id", scooter.id()).param("type", scooter.type())
                .param("hub", scooter.hubId()).param("status", scooter.status().name())
                .param("battery", scooter.batteryPercent()).update();
        if (updated == 0) {
            throw new NotFoundException("Scooter not found");
        }
        return scooter;
    }

    @Override
    public boolean hasSpace(Long hubId) {
        int capacity = jdbc.sql("SELECT capacity FROM hubs WHERE id = :id").param("id", hubId)
                .query(Integer.class).optional()
                .orElseThrow(() -> new NotFoundException("Hub not found"));
        long occupied = jdbc.sql("SELECT count(*) FROM scooters WHERE hub_id = :id AND status <> 'RETIRED'")
                .param("id", hubId).query(Long.class).single();
        return occupied < capacity;
    }

}
