package com.rayvolt.backend.mqtt;

import com.rayvolt.backend.model.CommandReply;
import com.rayvolt.backend.model.PositionStatus;
import com.rayvolt.backend.model.ScooterCommand;
import com.rayvolt.backend.model.TelemetryReading;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@Tag("PT-46")
class MqttScooterAdapterTest {
    private static final String TOPIC = "rayvolt/scooters/7/telemetry";
    private static final String SIMULATOR_MESSAGE = """
            {"scooterId": 7, "timestamp": "2026-11-22T10:00:00.123456+00:00", "battery": 87, "locked": false,
             "speedKmh": 14.2, "hubId": null, "lat": 40.6105, "lon": -8.716, "status": "MOVING", "fault": false}""";

    @Test
    void simulatorTelemetryIsRead() {
        TelemetryReading reading = MqttScooterAdapter.parseTelemetry(TOPIC, SIMULATOR_MESSAGE);
        assertThat(reading).isEqualTo(new TelemetryReading(7L, Instant.parse("2026-11-22T10:00:00.123456Z"), 87,
                false, 14.2, null, 40.6105, -8.716, PositionStatus.MOVING, false));
    }

    @Test
    void missingFieldIsRejected() {
        String noBattery = SIMULATOR_MESSAGE.replace("\"battery\": 87,", "");
        assertThatThrownBy(() -> MqttScooterAdapter.parseTelemetry(TOPIC, noBattery))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void unknownStatusIsRejected() {
        String flying = SIMULATOR_MESSAGE.replace("MOVING", "FLYING");
        assertThatThrownBy(() -> MqttScooterAdapter.parseTelemetry(TOPIC, flying))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void outOfRangeBatteryIsRejected() {
        String tooFull = SIMULATOR_MESSAGE.replace("87", "120");
        assertThatThrownBy(() -> MqttScooterAdapter.parseTelemetry(TOPIC, tooFull))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void scooterIdMustMatchTheTopic() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MqttScooterAdapter.parseTelemetry("rayvolt/scooters/8/telemetry", SIMULATOR_MESSAGE));
    }

    @Test
    void invalidJsonIsRejected() {
        assertThatThrownBy(() -> MqttScooterAdapter.parseTelemetry(TOPIC, "not json"))
                .isInstanceOf(RuntimeException.class);
    }

    private static final String EVENTS_TOPIC = "rayvolt/scooters/7/events";
    private static final String FAULT_EVENT = """
            {"scooterId": 7, "timestamp": "2026-11-22T10:00:00.123456+00:00", "type": "FAULT"}""";

    @Test
    void simulatorFaultEventIsRead() {
        assertThat(MqttScooterAdapter.parseFault(EVENTS_TOPIC, FAULT_EVENT)).isEqualTo(7L);
    }

    @Test
    void otherEventTypesAreRejected() {
        String other = FAULT_EVENT.replace("FAULT", "CRASH");
        assertThatIllegalArgumentException().isThrownBy(() -> MqttScooterAdapter.parseFault(EVENTS_TOPIC, other));
    }

    @Test
    void faultEventIdMustMatchTheTopic() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MqttScooterAdapter.parseFault("rayvolt/scooters/8/events", FAULT_EVENT));
    }

    @Test
    void faultEventWithoutTypeIsRejected() {
        String noType = FAULT_EVENT.replace(", \"type\": \"FAULT\"", "");
        assertThatThrownBy(() -> MqttScooterAdapter.parseFault(EVENTS_TOPIC, noType))
                .isInstanceOf(RuntimeException.class);
    }

    private static final String RESPONSES_TOPIC = "rayvolt/scooters/7/responses";
    private static final String REJECTED_REPLY = """
            {"scooterId": 7, "timestamp": "2026-11-22T10:00:00.123456+00:00", "commandId": "abc",
             "command": "UNLOCK", "status": "REJECTED", "reason": "SCOOTER_FAULT"}""";

    @Test
    void simulatorRejectedReplyIsRead() {
        assertThat(MqttScooterAdapter.parseReply(RESPONSES_TOPIC, REJECTED_REPLY)).isEqualTo(new CommandReply(7L,
                Instant.parse("2026-11-22T10:00:00.123456Z"), "abc", ScooterCommand.UNLOCK, CommandReply.Status.REJECTED,
                "SCOOTER_FAULT"));
    }

    @Test
    void completedReplyHasNoReason() {
        String completed = REJECTED_REPLY.replace("REJECTED", "COMPLETED").replace("\"SCOOTER_FAULT\"", "null");
        CommandReply reply = MqttScooterAdapter.parseReply(RESPONSES_TOPIC, completed);
        assertThat(reply.rejected()).isFalse();
        assertThat(reply.reason()).isNull();
    }

    @Test
    void replyIdMustMatchTheTopic() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MqttScooterAdapter.parseReply("rayvolt/scooters/8/responses", REJECTED_REPLY));
    }

    @Test
    void replyWithoutCommandIdIsRejected() {
        String noId = REJECTED_REPLY.replace("\"commandId\": \"abc\",", "");
        assertThatThrownBy(() -> MqttScooterAdapter.parseReply(RESPONSES_TOPIC, noId))
                .isInstanceOf(RuntimeException.class);
    }
}
