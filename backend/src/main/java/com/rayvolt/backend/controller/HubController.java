package com.rayvolt.backend.controller;

import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.HubDetails;
import com.rayvolt.backend.service.HubService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/hubs")
// TODO: restrict writes to operators when the Users module provides authorization.
public class HubController {
    private final HubService service;

    public HubController(HubService service) { this.service = service; }

    @Operation(summary = "List hubs", description = "All hubs ordered by name, visible to users.")
    @GetMapping
    public List<Hub> list() { return service.list(); }

    @Operation(summary = "Get a hub")
    @ApiResponse(responseCode = "404", description = "Hub not found")
    @GetMapping("/{id}")
    public Hub get(@PathVariable Long id) { return service.get(id); }

    @Operation(summary = "Add a hub", description = "Names are trimmed and case-sensitive unique. Capacity must be positive; both coordinates are required.")
    @ApiResponse(responseCode = "201", description = "Hub created; Location header identifies the resource")
    @ApiResponse(responseCode = "400", description = "Invalid hub details")
    @ApiResponse(responseCode = "409", description = "Name already in use")
    @PostMapping
    public ResponseEntity<Hub> add(@RequestBody HubDetails request) {
        Hub hub = service.add(request);
        return ResponseEntity.created(URI.create("/api/hubs/" + hub.getId())).body(hub);
    }

    @Operation(summary = "Edit a hub", description = "Capacity must be at least the number of non-retired scooters assigned to the hub. Equality is allowed.")
    @ApiResponse(responseCode = "400", description = "Invalid hub details")
    @ApiResponse(responseCode = "404", description = "Hub not found")
    @ApiResponse(responseCode = "409", description = "Duplicate name or capacity below occupancy")
    @PutMapping("/{id}")
    public Hub edit(@PathVariable Long id, @RequestBody HubDetails request) { return service.edit(id, request); }
}
