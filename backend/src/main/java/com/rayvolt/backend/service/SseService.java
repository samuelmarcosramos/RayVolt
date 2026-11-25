package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.PositionStatus;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterLive;
import com.rayvolt.backend.model.ScooterState;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterRepository;
import com.rayvolt.backend.repository.ScooterStateRepository;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SseService {
    private final ScooterRepository scooters;
    private final ScooterStateRepository states;
    private final HubRepository hubs;
    private final List<SseEmitter> listeners = new CopyOnWriteArrayList<>();

    public SseService(ScooterRepository scooters, ScooterStateRepository states, HubRepository hubs) {
        this.scooters = scooters;
        this.states = states;
        this.hubs = hubs;
    }

    public List<ScooterLive> list() {
        Map<Long, ScooterState> byScooter = states.findAll().stream()
                .collect(Collectors.toMap(ScooterState::getScooterId, Function.identity()));
        Map<Long, String> hubNames = hubs.findAll().stream().collect(Collectors.toMap(Hub::getId, Hub::getName));
        return scooters.findAllByOrderByIdAsc().stream()
                .filter(scooter -> scooter.getStatus() != ScooterStatus.RETIRED)
                .map(scooter -> {
                    ScooterState state = byScooter.get(scooter.getId());
                    return ScooterLive.of(scooter, state, atHub(state) ? hubNames.get(state.getHubId()) : null);
                })
                .toList();
    }

    public ScooterLive get(Long id) {
        return scooters.findById(id)
                .filter(scooter -> scooter.getStatus() != ScooterStatus.RETIRED)
                .map(scooter -> live(scooter, states.findById(id).orElse(null)))
                .orElseThrow(() -> new NotFoundException("Scooter not found"));
    }

    private ScooterLive live(Scooter scooter, ScooterState state) {
        String hubName = atHub(state)
                ? hubs.findById(state.getHubId()).map(Hub::getName).orElse(null)
                : null;
        return ScooterLive.of(scooter, state, hubName);
    }

    private static boolean atHub(ScooterState state) {
        return state != null && state.getPositionStatus() == PositionStatus.AT_HUB && state.getHubId() != null;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        listeners.add(emitter);
        emitter.onCompletion(() -> listeners.remove(emitter));
        emitter.onTimeout(() -> listeners.remove(emitter));
        emitter.onError(error -> listeners.remove(emitter));
        return emitter;
    }

    public void publish(Scooter scooter, ScooterState state) {
        ScooterLive live = live(scooter, state);
        for (SseEmitter emitter : listeners) {
            try {
                emitter.send(SseEmitter.event().data(live, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException exception) {
                listeners.remove(emitter);
            }
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void closeAll() {
        listeners.forEach(SseEmitter::complete);
        listeners.clear();
    }

    int listenerCount() {
        return listeners.size();
    }
}
