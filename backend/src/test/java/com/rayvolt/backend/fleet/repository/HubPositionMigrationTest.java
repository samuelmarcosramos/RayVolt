package com.rayvolt.backend.fleet.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("PT-32")
class HubPositionMigrationTest {
    @Test
    void positionMigrationKeepsExistingHubsWithoutInventingCoordinates() throws Exception {
        String url = "jdbc:h2:mem:migration-" + UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO hubs (id, name, capacity) VALUES ('"
                        + UUID.randomUUID() + "', 'Existing synthetic hub', 2)");
                Flyway.configure().dataSource(url, "sa", "").load().migrate();
                try (var result = statement.executeQuery("SELECT name, capacity, lat, lon FROM hubs")) {
                    assertThat(result.next()).as("Existing hub survives migration").isTrue();
                    assertThat(result.getString("name")).isEqualTo("Existing synthetic hub");
                    assertThat(result.getInt("capacity")).isEqualTo(2);
                    assertThat(result.getObject("lat")).as("Unknown latitude stays unset").isNull();
                    assertThat(result.getObject("lon")).as("Unknown longitude stays unset").isNull();
                }
            }
        }
    }
}
