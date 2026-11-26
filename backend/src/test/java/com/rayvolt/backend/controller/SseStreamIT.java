package com.rayvolt.backend.controller;

import com.rayvolt.backend.model.PositionStatus;
import com.rayvolt.backend.model.TelemetryReading;
import com.rayvolt.backend.service.TelemetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("PT-46")
class SseStreamIT {
    @LocalServerPort private int port;
    @Autowired private TelemetryService telemetry;
    @Autowired private JdbcClient jdbc;

    @AfterEach
    void clearFleet() {
        jdbc.sql("DELETE FROM scooter_state_history").update();
        jdbc.sql("DELETE FROM scooters").update();
    }

    @Test
    void savedTelemetryAndAFaultReachAnOpenStreamWithoutReloading() throws Exception {
        jdbc.sql("INSERT INTO scooters (type, hub_id, status) VALUES ('Standard', 1, 'AVAILABLE')").update();
        Long scooter = jdbc.sql("SELECT max(id) FROM scooters").query(Long.class).single();
        BlockingQueue<String> events = new LinkedBlockingQueue<>();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/scooters/stream"))
                .header("Accept", "text/event-stream").build();
        CompletableFuture<HttpResponse<Void>> stream = client.sendAsync(request,
                HttpResponse.BodyHandlers.fromLineSubscriber(new LineCollector(events)));
        try {
            long deadline = System.currentTimeMillis() + 5_000;
            String first = null;
            while (first == null && System.currentTimeMillis() < deadline) {
                telemetry.record(new TelemetryReading(scooter, Instant.now(), 64, true, 0, 1L, 40.612, -8.7175,
                        PositionStatus.AT_HUB, false));
                first = events.poll(200, TimeUnit.MILLISECONDS);
            }
            assertThat(first).as("Reading arrives on the stream").contains("\"scooterId\":" + scooter,
                    "\"battery\":64", "\"status\":\"AVAILABLE\"");
            telemetry.recordFault(scooter);
            assertThat(nextWith(events, "\"status\":\"MAINTENANCE\"")).as("Fault arrives on the stream").isTrue();
        } finally {
            stream.cancel(true);
            client.shutdownNow();
        }
    }

    private static boolean nextWith(BlockingQueue<String> events, String text) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            String event = events.poll(200, TimeUnit.MILLISECONDS);
            if (event != null && event.contains(text)) {
                return true;
            }
        }
        return false;
    }

    private record LineCollector(BlockingQueue<String> events) implements Flow.Subscriber<String> {
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String line) {
            if (line.startsWith("data:")) {
                events.add(line.substring(5));
            }
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }
    }
}
