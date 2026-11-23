package com.rayvolt.backend.fleet.controller;

import com.rayvolt.backend.common.exception.BadRequestException;
import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.service.FleetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/scooters")
// TODO: restrict these operations to operators when the Users module provides authorization.
public class FleetController {
    private final FleetService service;

    public FleetController(FleetService service) {
        this.service = service;
    }

    @GetMapping
    public List<Scooter> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<Scooter> add(@RequestBody ScooterRequest request) {
        Scooter scooter = service.add(request.type(), request.hubId());
        return ResponseEntity.created(URI.create("/api/scooters/" + scooter.id())).body(scooter);
    }

    @PutMapping("/{id}")
    public Scooter edit(@PathVariable UUID id, @RequestBody ScooterRequest request) {
        return service.edit(id, request.type(), request.hubId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> retire(@PathVariable UUID id) {
        service.retire(id);
        return ResponseEntity.noContent().build();
    }

    public record ScooterRequest(String type, UUID hubId) {
        public ScooterRequest {
            if (type == null || type.isBlank() || hubId == null) {
                throw new BadRequestException("Scooter type and hub ID are required");
            }
        }
    }
}
