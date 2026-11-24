package com.rayvolt.backend.mqtt;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.rayvolt.backend.service.FleetSyncService;
import org.eclipse.paho.client.mqttv3.MqttClient;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "rayvolt.mqtt.enabled=true")
@Tag("PT-46")
class MqttScooterAdapterIT {
    private static final int PORT = freePort();
    private static GenericContainer<?> broker = startBroker();

    @Autowired private FleetSyncService sync;
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
}
