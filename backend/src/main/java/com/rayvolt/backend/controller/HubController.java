package com.rayvolt.backend.controller;

import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.HubDetails;
import com.rayvolt.backend.service.HubService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/hubs")
// TODO: restrict writes to operators when the Users module provides authorization.
public class HubController {
    private final HubService service;

    public HubController(HubService service) { this.service = service; }

    @GetMapping
    public List<Hub> list() { return service.list(); }

    @GetMapping("/{id}")
    public Hub get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    public ResponseEntity<Hub> add(@RequestBody HubDetails request) {
        Hub hub = service.add(request);
        return ResponseEntity.created(URI.create("/api/hubs/" + hub.getId())).body(hub);
    }

    @PutMapping("/{id}")
    public Hub edit(@PathVariable Long id, @RequestBody HubDetails request) { return service.edit(id, request); }
}
