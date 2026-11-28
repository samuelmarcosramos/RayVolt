package com.rayvolt.backend.controller;

import com.rayvolt.backend.model.TripBooking;
import com.rayvolt.backend.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService service;

    public BookingController(BookingService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a scooter for ten minutes", description = "Uses the authenticated rider ID and "
            + "server-owned account, policy and telemetry data. Returns the scooter ID, rider ID and exact "
            + "expiry instant. No payment hold or unlock is made. Returns 401 without authentication, "
            + "403 for account or policy restrictions, 404 for an unknown scooter, and 409 for an "
            + "unavailable scooter, low battery or an existing booking or trip.")
    public TripBooking book(Principal principal, @Valid @RequestBody BookingRequest request) {
        return service.book(RiderIdentity.requireId(principal), request.scooterId());
    }

    @DeleteMapping("/{scooterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel the rider's booking at no cost", description = "The path identifies the "
            + "booked scooter. Only the owner may cancel before expiry. Returns 401 without authentication, "
            + "403 for another rider's booking, 404 for a missing booking and 409 at or after expiry. "
            + "A scooter already moved to maintenance is not made available.")
    public void cancel(Principal principal, @PathVariable Long scooterId) {
        service.cancel(RiderIdentity.requireId(principal), scooterId);
    }

    public record BookingRequest(@NotNull @Positive Long scooterId) { }
}
