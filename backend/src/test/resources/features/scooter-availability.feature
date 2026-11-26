# Acceptance criteria
# The user picks a hub and, optionally, a type (Standard, Cargo, Patrol) and sees the available scooters there.
#    Each scooter shows its id, type and battery %.
#    Only scooters that are available, have at least the minimum battery (10% by default) and are allowed for the user's role are shown.
#    If none match, the page shows "No scooters available at this hub".

@PT-14
Feature: Find available scooters at a hub
  A user can select a hub and optionally a scooter type to find available scooters
  with sufficient battery.

  Scenario: Filter out scooters below the default battery threshold and outside the selected hub
    When the user searches hub 1 without a type
    Then the availability response contains scooter IDs "1, 3, 6"

  Scenario: Filter by scooter type and an inclusive minimum battery threshold
    When the user searches hub 1 for "Patrol" scooters with minimum battery 50
    Then the availability response contains scooter IDs "6"

  Scenario: Return an empty result when the hub and type have no matches
    When the user searches hub 2 for "Cargo" scooters with minimum battery 10
    Then the availability response is empty


