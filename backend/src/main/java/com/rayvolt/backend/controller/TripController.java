package com.rayvolt.backend.controller;

import com.rayvolt.backend.model.Trip;
import com.rayvolt.backend.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
// TODO: enforce trip ownership when Users is integrated.
public class TripController {
    private final TripService service;

    public TripController(TripService service) { this.service = service; }

    @PostMapping("/{id}/end")
    @Operation(summary = "End a trip", description = "Saves end time, telemetry position and distance, "
            + "and applicable charges. Requests a scooter lock after commit. "
            + "Returns 404 for an unknown trip or 409 for an ended trip or missing ride telemetry.")
    public Trip end(@PathVariable Long id) { return service.end(id); }
}
