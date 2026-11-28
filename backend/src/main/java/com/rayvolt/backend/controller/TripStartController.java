package com.rayvolt.backend.controller;

import com.rayvolt.backend.model.Trip;
import com.rayvolt.backend.service.TripStartService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/trips")
public class TripStartController {
    private final TripStartService service;

    public TripStartController(TripStartService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a trip", description = "Starts an available scooter at a hub or the rider's "
            + "unexpired reservation. The authenticated principal name is the persisted rider ID. "
            + "Policy, pricing and deposit requirements come from server data. Holds EUR 20 in the local "
            + "payment sandbox for contractors and visitors before starting. Requests UNLOCK after commit. "
            + "Returns 401 without authentication, 403 for account or policy restrictions, 404 for an "
            + "unknown scooter and 409 for unavailable scooters, low battery or a failed deposit.")
    public Trip start(Principal principal, @Valid @RequestBody StartRequest request) {
        return service.start(RiderIdentity.requireId(principal), request.scooterId());
    }

    public record StartRequest(@NotNull @Positive Long scooterId) { }
}
