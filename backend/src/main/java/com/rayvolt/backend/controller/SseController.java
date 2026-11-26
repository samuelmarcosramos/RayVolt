package com.rayvolt.backend.controller;

import com.rayvolt.backend.model.ScooterLive;
import com.rayvolt.backend.service.SseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequestMapping("/api/scooters")
public class SseController {
    private final SseService service;

    public SseController(SseService service) {
        this.service = service;
    }

    @GetMapping("/state")
    public List<ScooterLive> list() {
        return service.list();
    }

    @GetMapping("/{id}/state")
    public ScooterLive get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return service.subscribe();
    }
}
