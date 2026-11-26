package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.BadRequestException;
import io.swagger.v3.oas.annotations.media.Schema;

public record HubDetails(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 120) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 120) String department,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Integer capacity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "-90", maximum = "90") Double lat,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "-180", maximum = "180") Double lon) {
    public HubDetails {
        if (name == null || name.isBlank() || name.strip().length() > 120
                || department == null || department.isBlank() || department.strip().length() > 120) {
            throw new BadRequestException("Hub name and department must contain 1 to 120 characters");
        }
        if (capacity == null || capacity <= 0) {
            throw new BadRequestException("Hub capacity must be greater than 0");
        }
        if (lat == null || lon == null || !Double.isFinite(lat) || !Double.isFinite(lon)
                || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new BadRequestException("Hub latitude must be between -90 and 90 and longitude between -180 and 180");
        }
        name = name.strip();
        department = department.strip();
    }
}
