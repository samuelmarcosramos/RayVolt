package com.rayvolt.backend.fleet.controller;

import com.rayvolt.backend.common.exception.BadRequestException;
import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.model.ScooterStateChange;
import com.rayvolt.backend.fleet.model.ScooterStatus;
import com.rayvolt.backend.fleet.model.StateChangeCause;
import com.rayvolt.backend.fleet.service.FleetService;
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
    @Operation(summary = "Change scooter state", description = "Applies the PT-34 lifecycle and records the cause. Reserved and in-use scooters cannot be retired. Invalid transitions return 409.")
    public Scooter changeStatus(@PathVariable Long id, @RequestBody StateChangeRequest request) {
        return service.changeStatus(id, request.status(), request.cause());
    }

    @GetMapping("/state-history")
    @Operation(summary = "Read scooter state history", description = "Returns changes in recording order. A null fromStatus denotes creation; pre-existing history is not fabricated.")
    public List<ScooterStateChange> history(@PathVariable Long id) {
        return service.history(id);
    }

    public record StateChangeRequest(ScooterStatus status, StateChangeCause cause) {
        public StateChangeRequest {
            if (status == null || cause == null) {
                throw new BadRequestException("Target state and cause are required");
            }
        }
    }
}
