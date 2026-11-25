@PT-34
Feature: Enforce the scooter lifecycle and retain its history

  Scenario Outline: Allowed state changes are saved with time and cause
    Given a scooter in state <from>
    When its state is changed to <to> because of OPERATOR
    Then the scooter is in state <to>
    And one state change is recorded with the fixed time and cause OPERATOR

    Examples:
      | from        | to          |
      | AVAILABLE   | RESERVED    |
      | RESERVED    | AVAILABLE   |
      | AVAILABLE   | IN_USE      |
      | RESERVED    | IN_USE      |
      | IN_USE      | AVAILABLE   |
      | AVAILABLE   | MAINTENANCE |
      | RESERVED    | MAINTENANCE |
      | IN_USE      | MAINTENANCE |
      | MAINTENANCE | AVAILABLE   |
      | AVAILABLE   | RETIRED     |
      | RESERVED    | RETIRED     |
      | MAINTENANCE | RETIRED     |

  Scenario Outline: Every other change is rejected without modifying state or history
    Given a scooter in state <from>
    When its state is changed to <to> because of USER
    Then the change is rejected as a conflict
    And the scooter is in state <from>
    And no state change is recorded

    Examples:
      | from        | to          |
      | AVAILABLE   | AVAILABLE   |
      | RESERVED    | RESERVED    |
      | IN_USE      | RESERVED    |
      | IN_USE      | IN_USE      |
      | IN_USE      | RETIRED     |
      | MAINTENANCE | RESERVED    |
      | MAINTENANCE | IN_USE      |
      | MAINTENANCE | MAINTENANCE |
      | RETIRED     | AVAILABLE   |
      | RETIRED     | RESERVED    |
      | RETIRED     | IN_USE      |
      | RETIRED     | MAINTENANCE |
      | RETIRED     | RETIRED     |

  Scenario Outline: State changes retain each supported cause
    Given a scooter in state AVAILABLE
    When its state is changed to MAINTENANCE because of <cause>
    Then the scooter is in state MAINTENANCE
    And one state change is recorded with the fixed time and cause <cause>

    Examples:
      | cause     |
      | USER      |
      | OPERATOR  |
      | TICKET    |
      | TELEMETRY |
