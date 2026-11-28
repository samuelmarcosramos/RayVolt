@PT-17
Feature: End a trip using telemetry
  Scenario Outline: Persist end details, release the scooter and apply charges
    Given a persisted trip lasting <seconds> seconds ending <location> with damage <damage>
    When the trip is ended
    Then the persisted trip has end telemetry, scooter state <state>, no-hub charge <charge> and <minutes> overtime minutes

    Examples:
      | seconds | location | damage | state       | charge | minutes |
      | 600     | at-hub   | false  | AVAILABLE   | 0      | 0       |
      | 600     | at-hub   | true   | MAINTENANCE | 0      | 0       |
      | 600     | outside  | false  | AVAILABLE   | 5      | 0       |
      | 2099    | at-hub   | false  | AVAILABLE   | 0      | 0       |
      | 2100    | at-hub   | false  | AVAILABLE   | 0      | 0       |
      | 2101    | at-hub   | false  | AVAILABLE   | 0      | 1       |
      | 2160    | outside  | false  | AVAILABLE   | 5      | 1       |
      | 2161    | outside  | true   | MAINTENANCE | 5      | 2       |

  Scenario: Concurrent requests cannot end or charge the same trip twice
    Given a persisted trip lasting 2101 seconds ending outside with damage false
    When two requests end the trip concurrently
    Then the persisted trip has end telemetry, scooter state AVAILABLE, no-hub charge 5 and 1 overtime minutes
