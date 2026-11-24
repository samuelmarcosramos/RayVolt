package com.rayvolt.backend.mqtt;

import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.service.ScooterGateway;
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
import tools.jackson.databind.json.JsonMapper;
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
    private static final long TIMEOUT_MILLIS = 5000;
    private final MqttPahoMessageHandler publisher;
    private final MqttPahoMessageDrivenChannelAdapter subscriber;
    private final List<Runnable> connectedActions = new CopyOnWriteArrayList<>();
    private final JsonMapper json = new JsonMapper();

    public MqttScooterAdapter(@Value("${rayvolt.mqtt.url}") String url, BeanFactory beanFactory,
            ApplicationEventPublisher events) {
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
        received.subscribe(message -> LOG.debug("Received on {}: {}",
                message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC), message.getPayload()));
        subscriber = new MqttPahoMessageDrivenChannelAdapter(clientId + "-in", factory, TELEMETRY_TOPIC);
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
        publisher.handleMessage(MessageBuilder.withPayload(json.writeValueAsString(body))
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
