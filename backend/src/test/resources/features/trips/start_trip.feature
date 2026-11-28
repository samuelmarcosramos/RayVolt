@PT-16
Feature: Start a trip using server-owned rider, policy and telemetry data
  Scenario Outline: Start an available scooter with the correct deposit requirement
    Given a startable <category> rider and a scooter in state AVAILABLE with battery <battery>
    When the authenticated rider starts the scooter
    Then trip start returns 201
    And the trip start time, hub, rider and IN_USE state are persisted with an unlock request
    And the sandbox contains <holds> holds totalling "<amount>" EUR

    Examples:
      | category       | battery | holds | amount |
      | WORKER         | 10      | 0     | 0.00   |
      | SECURITY_GUARD | 11      | 0     | 0.00   |
      | CONTRACTOR     | 80      | 1     | 20.00  |
      | VISITOR        | 80      | 1     | 20.00  |

  Scenario: Start the rider's unexpired booking
    Given a startable WORKER rider and a scooter in state RESERVED with battery 80
    And the scooter reservation belongs to self and expires in 1 seconds
    When the authenticated rider starts the scooter
    Then trip start returns 201
    And the trip start time, hub, rider and IN_USE state are persisted with an unlock request

  Scenario Outline: Reject another rider's booking or an expired booking
    Given a startable WORKER rider and a scooter in state RESERVED with battery 80
    And the scooter reservation belongs to <owner> and expires in <seconds> seconds
    When the authenticated rider starts the scooter
    Then trip start returns 409
    And no trip is saved and the scooter state and sandbox balance "40.00" stay unchanged

    Examples:
      | owner | seconds |
      | other | 600     |
      | self  | 0       |
      | self  | -1      |

  Scenario: Reject battery just below the minimum
    Given a startable WORKER rider and a scooter in state AVAILABLE with battery 9
    When the authenticated rider starts the scooter
    Then trip start returns 409
    And no trip is saved and the scooter state and sandbox balance "40.00" stay unchanged

  Scenario: Reject a type the rider cannot use
    Given a startable WORKER rider and a scooter in state AVAILABLE with battery 80
    And the rider policy allows only type "Cargo"
    When the authenticated rider starts the scooter
    Then trip start returns 403
    And no trip is saved and the scooter state and sandbox balance "40.00" stay unchanged

  Scenario: Reject use exactly at the closing hour
    Given a startable WORKER rider and a scooter in state AVAILABLE with battery 80
    And the rider policy allows use from "08:00" until "10:00"
    When the authenticated rider starts the scooter
    Then trip start returns 403
    And no trip is saved and the scooter state and sandbox balance "40.00" stay unchanged

  Scenario: Allow use exactly at the opening hour
    Given a startable WORKER rider and a scooter in state AVAILABLE with battery 80
    And the rider policy allows use from "10:00" until "18:00"
    When the authenticated rider starts the scooter
    Then trip start returns 201
    And the trip start time, hub, rider and IN_USE state are persisted with an unlock request

  Scenario: Reject an expired external account
    Given a startable VISITOR rider and a scooter in state AVAILABLE with battery 80
    And the rider account expired yesterday
    When the authenticated rider starts the scooter
    Then trip start returns 403
    And no trip is saved and the scooter state and sandbox balance "40.00" stay unchanged

  Scenario: A refused deposit leaves no trip, state change or hold
    Given a startable CONTRACTOR rider and a scooter in state AVAILABLE with battery 80
    And the rider sandbox balance is "19.99" EUR
    When the authenticated rider starts the scooter
    Then trip start returns 409
    And no trip is saved and the scooter state and sandbox balance "19.99" stay unchanged

  Scenario: An exact deposit balance is sufficient
    Given a startable VISITOR rider and a scooter in state AVAILABLE with battery 80
    And the rider sandbox balance is "20.00" EUR
    When the authenticated rider starts the scooter
    Then trip start returns 201
    And the trip start time, hub, rider and IN_USE state are persisted with an unlock request
    And the sandbox contains 1 holds totalling "20.00" EUR

  Scenario: Two riders cannot start the same scooter
    Given a startable CONTRACTOR rider and a scooter in state AVAILABLE with battery 80
    When two riders start the same scooter concurrently
    Then the sandbox contains 1 holds totalling "20.00" EUR

  Scenario: A rider cannot start two trips concurrently
    Given a startable CONTRACTOR rider and a scooter in state AVAILABLE with battery 80
    When the same rider starts two scooters concurrently
    Then the sandbox contains 1 holds totalling "20.00" EUR
