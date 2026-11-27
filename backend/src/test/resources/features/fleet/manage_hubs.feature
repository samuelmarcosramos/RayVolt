@PT-33
Feature: Operators maintain hubs visible to users

  Scenario: Add a hub with all required details
    When an operator adds hub "North Gate" in department "Security" with capacity 3 at 40.6, -8.7
    Then the hub list contains "North Gate" in "Security" with capacity 3 at 40.6, -8.7

  Scenario: Hub names are unique
    Given a managed hub with 2 active scooters
    When an operator adds another hub with the same name
    Then the hub change is rejected as a conflict

  Scenario Outline: Capacity must be positive
    When an operator supplies hub capacity <capacity>
    Then the hub details are rejected
    Examples:
      | capacity |
      | -1       |
      | 0        |

  Scenario: Capacity cannot fall below current occupancy
    Given a managed hub with 2 active scooters
    When an operator changes its capacity to 1
    Then the hub change is rejected as a conflict
    And the stored hub capacity is 3

  Scenario Outline: Capacity can equal or exceed current occupancy
    Given a managed hub with 2 active scooters
    When an operator changes its capacity to <capacity>
    Then the stored hub capacity is <capacity>
    Examples:
      | capacity |
      | 2        |
      | 3        |

  Scenario: Editing updates every hub detail in the user list
    Given a managed hub with 2 active scooters
    When an operator updates its name, department and location
    Then the hub list contains "South Gate" in "Logistics" with capacity 2 at -90.0, 180.0

  Scenario: Retired scooters do not consume hub capacity
    Given a managed hub with 2 active scooters
    And one of its scooters is retired
    When an operator changes its capacity to 1
    Then the stored hub capacity is 1
