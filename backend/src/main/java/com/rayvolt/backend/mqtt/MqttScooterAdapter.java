package com.rayvolt.backend.mqtt;

import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.TelemetryReading;
import com.rayvolt.backend.service.ScooterGateway;
import com.rayvolt.backend.service.TelemetryService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(name = "rayvolt.mqtt.enabled", havingValue = "true")
public class MqttScooterAdapter implements ScooterGateway, DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(MqttScooterAdapter.class);
    private static final String TELEMETRY_TOPIC = "rayvolt/scooters/+/telemetry";
    private static final String EVENTS_TOPIC = "rayvolt/scooters/+/events";
    private static final long TIMEOUT_MILLIS = 5000;
    private final MqttPahoMessageHandler publisher;
    private final MqttPahoMessageDrivenChannelAdapter subscriber;
    private final List<Runnable> connectedActions = new CopyOnWriteArrayList<>();
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    private final TelemetryService telemetry;

    public MqttScooterAdapter(@Value("${rayvolt.mqtt.url}") String url, BeanFactory beanFactory,
            ApplicationEventPublisher events, TelemetryService telemetry) {
        this.telemetry = telemetry;
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{url});
        options.setAutomaticReconnect(true);
        options.setMaxReconnectDelay((int) TIMEOUT_MILLIS);
        options.setCleanSession(true);
        options.setConnectionTimeout(5);
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        String clientId = "rayvolt-backend-" + UUID.randomUUID();

        publisher = new MqttPahoMessageHandler(clientId + "-out", factory);
        publisher.setDefaultQos(1);
        publisher.setCompletionTimeout(TIMEOUT_MILLIS);
        publisher.setApplicationEventPublisher(events);
        publisher.setBeanFactory(beanFactory);
        publisher.afterPropertiesSet();
        publisher.start();

        DirectChannel received = new DirectChannel();
        received.subscribe(message -> receive(
                (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC), (String) message.getPayload()));
        subscriber = new MqttPahoMessageDrivenChannelAdapter(clientId + "-in", factory, TELEMETRY_TOPIC, EVENTS_TOPIC);
        subscriber.setQos(1);
        subscriber.setCompletionTimeout(TIMEOUT_MILLIS);
        subscriber.setDisconnectCompletionTimeout(TIMEOUT_MILLIS);
        subscriber.setQuiescentTimeout(0);
        subscriber.setOutputChannel(received);
        subscriber.setApplicationEventPublisher(events);
        subscriber.setBeanFactory(beanFactory);
        subscriber.afterPropertiesSet();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        subscriber.start();
    }

    @EventListener
    public void onSubscribed(MqttSubscribedEvent event) {
        if (event.getSource() == subscriber) {
            connectedActions.forEach(Runnable::run);
        }
    }

    @Override
    public void onConnected(Runnable action) {
        connectedActions.add(action);
    }

    private void receive(String topic, String payload) {
        try {
            if (topic.endsWith("/events")) {
                telemetry.recordFault(parseFault(topic, payload));
            } else {
                telemetry.record(parseTelemetry(topic, payload));
            }
        } catch (RuntimeException exception) {
            LOG.warn("Ignored message on {}: {}", topic, exception.getMessage());
        }
    }

    static TelemetryReading parseTelemetry(String topic, String payload) {
        TelemetryReading reading = JSON.readValue(payload, TelemetryReading.class);
        requireTopicId(topic, reading.scooterId());
        return reading;
    }

    static Long parseFault(String topic, String payload) {
        EventMessage event = JSON.readValue(payload, EventMessage.class);
        if (!"FAULT".equals(event.type())) {
            throw new IllegalArgumentException("unknown event type " + event.type());
        }
        requireTopicId(topic, event.scooterId());
        return event.scooterId();
    }

    private static void requireTopicId(String topic, Long scooterId) {
        if (!topic.split("/")[2].equals(String.valueOf(scooterId))) {
            throw new IllegalArgumentException("scooterId " + scooterId + " does not match the topic");
        }
    }

    record EventMessage(Long scooterId, Instant timestamp, String type) {
    }

    @Override
    public void publishHubs(List<Hub> hubs) {
        List<Map<String, Object>> body = hubs.stream().map(hub -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", hub.getId());
            item.put("name", hub.getName());
            item.put("lat", hub.getLat());
            item.put("lon", hub.getLon());
            return item;
        }).toList();
        publishRetained("rayvolt/hubs", body);
    }

    @Override
    public void registerScooter(Long scooterId, Long hubId) {
        publishRetained("rayvolt/fleet/" + scooterId, Map.of("hubId", hubId));
    }

    @Override
    public void retireScooter(Long scooterId, Long hubId) {
        publishRetained("rayvolt/fleet/" + scooterId, Map.of("hubId", hubId, "status", "RETIRED"));
    }

    private void publishRetained(String topic, Object body) {
        publisher.handleMessage(MessageBuilder.withPayload(JSON.writeValueAsString(body))
                .setHeader(MqttHeaders.TOPIC, topic)
                .setHeader(MqttHeaders.RETAINED, true)
                .build());
    }

    @Override
    public void destroy() {
        subscriber.stop();
        subscriber.destroy();
        publisher.stop();
    }
}
