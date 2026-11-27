package com.rayvolt.backend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterStateChange;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.service.FleetService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/scooters/{id}")
// TODO: enforce operator authorization when Users is integrated.
public class ScooterLifecycleController {
    private final FleetService service;

    public ScooterLifecycleController(FleetService service) {
        this.service = service;
    }

    @PatchMapping("/state")
    @Operation(summary = "Change scooter state", description = "Changes the scooter state and records the operator cause. Returns 409 for a change that is not allowed.")
    public Scooter changeStatus(@PathVariable Long id, @Valid @RequestBody StateChangeRequest request) {
        return service.changeOperatorStatus(id, request.status());
    }

    @GetMapping("/state-history")
    @Operation(summary = "Read scooter state history", description = "Returns the state changes of a scooter, oldest first.")
    public List<ScooterStateChange> history(@PathVariable Long id) {
        return service.history(id);
    }

    public record StateChangeRequest(@NotNull ScooterStatus status) {
    }
}
