package com.rayvolt.backend.controller;

import com.rayvolt.backend.errors.UnauthorizedException;
import java.security.Principal;

final class RiderIdentity {
    private RiderIdentity() { }

    static Long requireId(Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Login is required");
        }
        try {
            Long id = Long.valueOf(principal.getName());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException("Authenticated principal must identify a rider");
        }
    }
}
