package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.ScooterStateChange;
import com.rayvolt.backend.fleet.model.ScooterStatus;
import com.rayvolt.backend.fleet.model.StateChangeCause;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcScooterStateHistoryRepository implements ScooterStateHistoryRepository {
    private final JdbcClient jdbc;

    public JdbcScooterStateHistoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(ScooterStateChange change) {
        jdbc.sql("""
                INSERT INTO scooter_state_history (scooter_id, from_status, to_status, changed_at, cause)
                VALUES (:scooter, :from, :to, :at, :cause)
                """)
                .param("scooter", change.scooterId())
                .param("from", change.fromStatus() == null ? null : change.fromStatus().name())
                .param("to", change.toStatus().name())
                .param("at", Timestamp.from(change.changedAt()))
                .param("cause", change.cause().name()).update();
    }

    @Override
    public List<ScooterStateChange> findByScooterId(Long scooterId) {
        return jdbc.sql("SELECT * FROM scooter_state_history WHERE scooter_id = :id ORDER BY id")
                .param("id", scooterId).query((rs, row) -> {
                    String from = rs.getString("from_status");
                    return new ScooterStateChange(rs.getLong("id"), rs.getLong("scooter_id"),
                            from == null ? null : ScooterStatus.valueOf(from),
                            ScooterStatus.valueOf(rs.getString("to_status")),
                            rs.getTimestamp("changed_at").toInstant(), StateChangeCause.valueOf(rs.getString("cause")));
                }).list();
    }
}
