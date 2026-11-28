@PT-15
Feature: Book an available scooter for ten minutes
  Background:
    Given two synthetic riders and two available scooters for bookings

  Scenario: Reserve an available scooter without payment or unlocking
    When the first rider books the first scooter
    Then booking request returns 201
    And the first scooter is reserved by the first rider until exactly ten minutes after booking
    And booking and cancellation have not charged or unlocked either scooter

  Scenario Outline: Expiry includes the exact ten-minute boundary
    Given the first rider has booked the first scooter
    When the booking clock advances by <milliseconds> milliseconds
    And the booking expiry job runs
    Then the first scooter has state <state> and <count> persisted bookings
    And booking and cancellation have not charged or unlocked either scooter

    Examples:
      | milliseconds | state     | count |
      | 599999       | RESERVED  | 1     |
      | 600000       | AVAILABLE | 0     |
      | 600001       | AVAILABLE | 0     |

  Scenario: Cancel just before expiry at no cost
    Given the first rider has booked the first scooter
    When the booking clock advances by 599999 milliseconds
    And the first rider cancels the first scooter booking
    Then booking request returns 204
    And the first scooter has state AVAILABLE and 0 persisted bookings
    And booking and cancellation have not charged or unlocked either scooter

  Scenario: Another rider cannot cancel the booking
    Given the first rider has booked the first scooter
    When the second rider cancels the first scooter booking
    Then booking request returns 403
    And the first scooter has state RESERVED and 1 persisted bookings

  Scenario: Cancellation is rejected exactly at expiry
    Given the first rider has booked the first scooter
    When the booking clock advances by 600000 milliseconds
    And the first rider cancels the first scooter booking
    Then booking request returns 409
    When the booking expiry job runs
    Then the first scooter has state AVAILABLE and 0 persisted bookings

  Scenario: A rider cannot have two active bookings
    Given the first rider has booked the first scooter
    When the first rider books the second scooter
    Then booking request returns 409
    And the first scooter has state RESERVED and 1 persisted bookings

  Scenario: An active trip prevents another booking
    Given the first rider has an active trip on the first scooter
    When the first rider books the second scooter
    Then booking request returns 409
    And the rider still has exactly one active trip and no bookings

  Scenario: Two riders cannot reserve the same scooter
    When two riders book the same scooter concurrently
    Then booking and cancellation have not charged or unlocked either scooter

  Scenario: Concurrent requests cannot give one rider two bookings
    When one rider books two scooters concurrently
    Then booking and cancellation have not charged or unlocked either scooter

  Scenario: A booking and a trip cannot start concurrently for one rider
    When one rider books and starts different scooters concurrently

  Scenario: An expiry check cannot undo a started trip
    Given the first rider has booked the first scooter
    When the booking clock advances by 599000 milliseconds
    And the first rider starts the booked scooter
    And the booking clock advances by 600000 milliseconds
    And a delayed expiry check runs for the first scooter
    Then the first scooter has state IN_USE and 0 persisted bookings

  Scenario: An expiry check cannot delete a replacement booking
    Given the first rider has booked the first scooter
    When the booking clock advances by 600000 milliseconds
    And the second rider replaces the expired booking
    And a delayed expiry check runs for the first scooter
    Then the replacement booking remains owned by the second rider

  Scenario: Expiry preserves maintenance after a telemetry fault
    Given the first rider has booked the first scooter
    When telemetry reports a fault on the reserved scooter
    And the booking clock advances by 600000 milliseconds
    And the booking expiry job runs
    Then the first scooter has state MAINTENANCE and 0 persisted bookings

  Scenario: Cancellation preserves maintenance after a telemetry fault
    Given the first rider has booked the first scooter
    When telemetry reports a fault on the reserved scooter
    And the first rider cancels the first scooter booking
    Then booking request returns 204
    And the first scooter has state MAINTENANCE and 0 persisted bookings
    And booking and cancellation have not charged or unlocked either scooter
