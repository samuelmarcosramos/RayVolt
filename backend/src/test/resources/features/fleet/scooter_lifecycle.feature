@PT-34
Feature: Scooter lifecycle
  Scenario: Valid changes preserve an ordered history with their time and cause
    Given a scooter in state AVAILABLE
    When the scooter follows these lifecycle changes
      | state       | cause     |
      | RESERVED    | USER      |
      | AVAILABLE   | USER      |
      | IN_USE      | USER      |
      | AVAILABLE   | USER      |
      | MAINTENANCE | OPERATOR  |
      | AVAILABLE   | TICKET    |
      | RESERVED    | USER      |
      | IN_USE      | USER      |
      | MAINTENANCE | TELEMETRY |
      | AVAILABLE   | TICKET    |
      | RESERVED    | USER      |
      | MAINTENANCE | TELEMETRY |
      | AVAILABLE   | TICKET    |
      | RESERVED    | USER      |
      | RETIRED     | OPERATOR  |
    Then the scooter is retired and every change has its expected audit record

  Scenario: Forbidden changes preserve the state and history, including retired scooters
    Then these lifecycle changes are rejected without saving anything
      | from        | to          |
      | AVAILABLE   | AVAILABLE   |
      | RESERVED    | RESERVED    |
      | IN_USE      | IN_USE      |
      | IN_USE      | RESERVED    |
      | IN_USE      | RETIRED     |
      | MAINTENANCE | MAINTENANCE |
      | MAINTENANCE | RESERVED    |
      | MAINTENANCE | IN_USE      |
      | RETIRED     | AVAILABLE   |
      | RETIRED     | RESERVED    |
      | RETIRED     | IN_USE      |
      | RETIRED     | MAINTENANCE |
      | RETIRED     | RETIRED     |
