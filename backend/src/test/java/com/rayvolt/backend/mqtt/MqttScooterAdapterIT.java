package com.rayvolt.backend.mqtt;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.rayvolt.backend.service.FleetService;
import com.rayvolt.backend.service.FleetSyncService;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "rayvolt.mqtt.enabled=true")
@Tag("PT-46")
class MqttScooterAdapterIT {
    private static final int PORT = freePort();
    private static GenericContainer<?> broker = startBroker();

    @Autowired private FleetSyncService sync;
    @Autowired private FleetService fleet;
    @Autowired private JdbcClient jdbc;

    @DynamicPropertySource
    static void brokerUrl(DynamicPropertyRegistry registry) {
        registry.add("rayvolt.mqtt.url", MqttScooterAdapterIT::brokerUrl);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static GenericContainer<?> startBroker() {
        GenericContainer<?> container = new GenericContainer<>("eclipse-mosquitto:2")
                .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
                .withExposedPorts(1883)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(PORT), new ExposedPort(1883))));
        container.start();
        return container;
    }

    private static String brokerUrl() {
        return "tcp://" + broker.getHost() + ":" + PORT;
    }

    @AfterEach
    void clearFleet() {
        jdbc.sql("DELETE FROM scooters").update();
    }

    @AfterAll
    static void stopBroker() {
        broker.stop();
    }

    private Map<String, String> retainedMessages(long waitMillis, String... expectedTopics) throws Exception {
        Map<String, String> messages = new ConcurrentHashMap<>();
        MqttClient client = new MqttClient(brokerUrl(), MqttClient.generateClientId(), new MemoryPersistence());
        client.connect();
        try {
            client.subscribe("rayvolt/#", 1, (topic, message) ->
                    messages.put(topic, new String(message.getPayload(), StandardCharsets.UTF_8)));
            long deadline = System.currentTimeMillis() + waitMillis;
            while (!messages.keySet().containsAll(List.of(expectedTopics))
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
        } finally {
            client.disconnect();
            client.close();
        }
        return messages;
    }

    private Long insertScooter(String type, long hubId, String status) {
        jdbc.sql("INSERT INTO scooters (type, hub_id, status) VALUES (:type, :hub, :status)")
                .param("type", type).param("hub", hubId).param("status", status).update();
        return jdbc.sql("SELECT max(id) FROM scooters").query(Long.class).single();
    }

    @Test
    void startupPublishesTheSeededHubsRetained() throws Exception {
        String hubs = retainedMessages(10_000, "rayvolt/hubs").get("rayvolt/hubs");
        assertThat(hubs).as("Retained hub list is on the broker").isNotNull();
        assertThat(hubs).contains("\"id\":1", "\"name\":\"Main Gate\"", "\"lat\":40.612", "\"lon\":-8.7175")
                .contains("Production Hall", "Warehouse", "Security Post")
                .doesNotContain("capacity");
    }

    @Test
    void activeAndRetiredScootersArePublishedRetained() throws Exception {
        Long active = insertScooter("Standard", 1, "AVAILABLE");
        Long retired = insertScooter("Patrol", 2, "RETIRED");
        sync.publishAll();
        Map<String, String> messages = retainedMessages(10_000, "rayvolt/fleet/" + active, "rayvolt/fleet/" + retired);
        assertThat(messages.get("rayvolt/fleet/" + active)).isEqualTo("{\"hubId\":1}");
        assertThat(messages.get("rayvolt/fleet/" + retired)).contains("\"hubId\":2", "\"status\":\"RETIRED\"");
    }

    @Test
    void aNewBrokerGetsTheHubsAndFleetAgainAfterReconnecting() throws Exception {
        Long scooter = insertScooter("Cargo", 3, "AVAILABLE");
        broker.stop();
        broker = startBroker();
        Map<String, String> messages = retainedMessages(30_000, "rayvolt/hubs", "rayvolt/fleet/" + scooter);
        assertThat(messages).as("Hubs and fleet are back on the new broker")
                .containsKeys("rayvolt/hubs", "rayvolt/fleet/" + scooter);
        assertThat(messages.get("rayvolt/fleet/" + scooter)).isEqualTo("{\"hubId\":3}");
    }

    @Test
    void createdAndRetiredScootersReachTheBroker() throws Exception {
        Long created = fleet.add("Standard", 2L).getId();
        assertThat(retainedMessages(10_000, "rayvolt/fleet/" + created).get("rayvolt/fleet/" + created))
                .isEqualTo("{\"hubId\":2}");
        fleet.retire(created);
        Map<String, String> after = new ConcurrentHashMap<>();
        long deadline = System.currentTimeMillis() + 10_000;
        while (!String.valueOf(after.get("rayvolt/fleet/" + created)).contains("RETIRED")
                && System.currentTimeMillis() < deadline) {
            after = retainedMessages(500, "rayvolt/fleet/" + created);
        }
        assertThat(after.get("rayvolt/fleet/" + created)).contains("\"hubId\":2", "\"status\":\"RETIRED\"");
    }

    private void publish(Long scooterId, String payload) throws Exception {
        MqttClient client = new MqttClient(brokerUrl(), MqttClient.generateClientId(), new MemoryPersistence());
        client.connect();
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            client.publish("rayvolt/scooters/" + scooterId + "/telemetry", message);
        } finally {
            client.disconnect();
            client.close();
        }
    }

    private String telemetry(Long scooterId, Instant timestamp, double lat, String status) {
        return """
                {"scooterId": %d, "timestamp": "%s", "battery": 90, "locked": %s, "speedKmh": %s,
                 "hubId": %s, "lat": %s, "lon": -8.7175, "status": "%s", "fault": false}"""
                .formatted(scooterId, timestamp, status.equals("AT_HUB"), status.equals("MOVING") ? "15" : "0",
                        status.equals("AT_HUB") ? "1" : "null", lat, status);
    }

    private Optional<Map<String, Object>> state(Long scooterId) {
        return jdbc.sql("SELECT * FROM scooter_state WHERE scooter_id = :id").param("id", scooterId)
                .query().listOfRows().stream().findFirst();
    }

    private Map<String, Object> waitForState(Long scooterId, String expectedStatus) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<Map<String, Object>> row = state(scooterId);
            if (row.isPresent() && expectedStatus.equals(row.get().get("POSITION_STATUS"))) {
                return row.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Scooter " + scooterId + " did not reach " + expectedStatus + " within 5 s");
    }

    @Test
    void telemetryIsSavedWithinFiveSecondsAndTheRideIsCounted() throws Exception {
        Long scooter = insertScooter("Standard", 1, "IN_USE");
        Instant start = Instant.parse("2026-11-24T10:00:00Z");
        publish(scooter, telemetry(scooter, start, 40.612, "MOVING"));
        publish(scooter, telemetry(scooter, start.plusSeconds(5), 40.613, "MOVING"));
        Instant sent = Instant.now();
        publish(scooter, telemetry(scooter, start.plusSeconds(10), 40.614, "AT_HUB"));
        Map<String, Object> row = waitForState(scooter, "AT_HUB");
        assertThat(Duration.between(sent, Instant.now())).as("Saved within 5 s").isLessThan(Duration.ofSeconds(5));
        assertThat((Double) row.get("ODOMETER_KM")).as("Both stretches, including the one into the hub")
                .isCloseTo(0.2224, org.assertj.core.data.Offset.offset(0.001));
        assertThat(row.get("RECEIVED_AT")).isNotNull();
    }

    @Test
    void invalidTelemetryIsIgnoredAndTheNextReadingStillArrives() throws Exception {
        Long scooter = insertScooter("Standard", 1, "AVAILABLE");
        publish(scooter, "not json");
        publish(scooter, telemetry(scooter, Instant.parse("2026-11-24T10:00:00Z"), 40.612, "AT_HUB"));
        assertThat(waitForState(scooter, "AT_HUB").get("BATTERY")).isEqualTo(90);
    }
}
