package com.rayvolt.backend.controller;

import com.rayvolt.backend.errors.BadRequestException;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.service.FleetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

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

    @GetMapping("/available")
    @Operation(summary = "Find available scooters at a hub",
            description = "Returns only AVAILABLE scooters at the selected hub with at least the requested battery. "
                    + "The minimum battery is inclusive and defaults to 10 percent; type is optional.")
    public List<Scooter> findAvailable(
            @Parameter(description = "Positive ID of the selected hub")
            @RequestParam Long hubId,
            @Parameter(description = "Optional scooter type: Standard, Cargo, or Patrol")
            @RequestParam(required = false) String type,
            @Parameter(description = "Inclusive battery threshold from 0 to 100; defaults to 10")
            @RequestParam(defaultValue = "10") int minBatteryPercent) {
        return service.findAvailable(hubId, type, minBatteryPercent);
    }

    @GetMapping("/{id}")
    public Scooter get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<Scooter> add(@RequestBody ScooterRequest request) {
        Scooter scooter = service.add(request.type(), request.hubId());
        return ResponseEntity.created(URI.create("/api/scooters/" + scooter.getId())).body(scooter);
    }

    @PutMapping("/{id}")
    public Scooter edit(@PathVariable Long id, @RequestBody ScooterRequest request) {
        return service.edit(id, request.type(), request.hubId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> retire(@PathVariable Long id) {
        service.retire(id);
        return ResponseEntity.noContent().build();
    }

    public record ScooterRequest(String type, Long hubId) {
        public ScooterRequest {
            if (type == null || type.isBlank() || hubId == null || hubId <= 0) {
                throw new BadRequestException("Scooter type and hub ID are required");
            }
        }
    }
}
