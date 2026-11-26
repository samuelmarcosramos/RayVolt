@PT-34
Feature: Scooter lifecycle
  Scenario Outline: Valid changes are saved with time and cause
    Given a persisted scooter in state <from>
    When the application changes its state to <to> with cause <cause>
    Then the new state and its timestamped cause are saved

    Examples:
      | from        | to          | cause     |
      | AVAILABLE   | RESERVED    | USER      |
      | IN_USE      | MAINTENANCE | TELEMETRY |
      | MAINTENANCE | AVAILABLE   | TICKET    |
      | MAINTENANCE | RETIRED     | OPERATOR  |

  Scenario Outline: Invalid changes leave the database unchanged
    Given a persisted scooter in state <from>
    When the application changes its state to <to> with cause OPERATOR
    Then the change is rejected and the stored state and history stay unchanged

    Examples:
      | from      | to        |
      | RESERVED  | RETIRED   |
      | IN_USE    | RETIRED   |
      | RETIRED   | AVAILABLE |
      | AVAILABLE | AVAILABLE |
