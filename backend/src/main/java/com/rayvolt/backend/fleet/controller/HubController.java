package com.rayvolt.backend.fleet.controller;

import com.rayvolt.backend.fleet.model.Hub;
import com.rayvolt.backend.fleet.service.FleetService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/hubs")
public class HubController {
    private final FleetService service;

    public HubController(FleetService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List hubs available for scooter search")
    public List<Hub> list() {
        return service.listHubs();
    }
}
