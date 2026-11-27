# Acceptance criteria
# The user picks a hub and, optionally, a type (Standard, Cargo, Patrol) and sees the available scooters there.
#    Each scooter shows its id, type and battery % (unknown until the first telemetry reading).
#    Only scooters that are available, have at least 10% battery and are allowed for the user's role are shown.
#    If none match, the page shows "No scooters available at this hub".
# Role-based filtering is deferred until PT-9, PT-10, PT-11, and PT-24 implement authenticated user roles.

@PT-14
Feature: Find available scooters at a hub
  A user can select a hub and optionally a scooter type to find available scooters
  with sufficient battery.

  Scenario: Filter out scooters below the default battery threshold and outside the selected hub
    When the user searches hub "Main Gate" without a type
    Then the availability response contains scooter IDs "7001, 7003, 7006"

  Scenario: Filter by scooter type
    When the user searches hub "Main Gate" for "Patrol" scooters
    Then the availability response contains scooter IDs "7006"

  Scenario: Return an empty result when the hub and type have no matches
    When the user searches hub "Production Hall" for "Cargo" scooters
    Then the availability response is empty
