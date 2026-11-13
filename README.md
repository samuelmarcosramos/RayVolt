# RayVolt

> Valdren Works gets e-scooters for its workers and guards without owning a fleet: RayVolt supplies, maintains and runs them.

## Project abstract

RayVolt is an e-scooter service for Valdren Works, a fictional industrial complex so large that walking between
departments wastes time. Valdren Works does not want to own or maintain a fleet, so it hires RayVolt, a fictional
operator, under a contract with agreed service levels (SLAs). RayVolt supplies the scooters and takes care of
maintenance, battery management and moving scooters between hubs.

Workers find scooters at hubs next to departments and gates, book one or start a trip right away, and end the trip
at a hub. Security guards use them for their patrol rounds and get priority when scooters are short. Valdren Works sets
the rules of use, such as how many scooters each department may book per shift, speed limits near hazard areas and
which workers may use which scooter types, and follows the service through KPIs and SLA compliance. Contractors and
visitors pay a deposit, and anyone who goes over the allowed time or damages a scooter is charged.

**Concretization:** service pool (client: the company that owns the industrial complex; usage: work trips and
security patrols between hubs inside the complex).

## Project team

| Name | Student no. | Role |
|---|---|---|
| Samuel Ramos | 124955 | Team Coordinator |
| Francisco Santos | 126255 | Product Owner |
| Kelvin Loforte | 121420 | QA Engineer |
| Mario Santos | 127560 | DevOps Master |

Every member is also a developer.

## Project bookmarks

| What | Link |
|---|---|
| Jira backlog | <https://ua-team-e2msb3m6.atlassian.net/jira/software/projects/PT/boards/2/backlog> |
| Related repositories | none |
| API documentation | not available yet |
| Static analysis dashboard | not available yet |
| Observability dashboard | not available yet |
| Deployed application | not available yet |

## SQA strategy and selected tools

The strategy follows the assignment's required practices while keeping the pull-request feedback loop practical.

| Quality activity | Selected tools | Scope |
|---|---|---|
| Static analysis and coverage | **SonarQube + JaCoCo** | Publish code-quality and coverage results to the SonarQube dashboard. The quality gate checks new code and blocks a merge on failure. |
| Dependency vulnerability scanning | **OWASP Dependency-Check** | Scan dependencies in CI on every build and fail the build according to the agreed vulnerability policy. |
| Unit tests | **JUnit 5 (Jupiter) + Mockito** | Test business logic in isolation; keep these tests fast and run them on every push and pull request. |
| Acceptance / BDD tests | **Cucumber** | Write core user-story acceptance criteria as Gherkin scenarios and automate them. |
| Integration tests | **Spring Boot Test + Testcontainers** | Test API and persistence against real containerized dependencies such as PostgreSQL; test simulator and payment adapters at their integration boundaries. Run separately from unit tests. |
| UI end-to-end tests | **Playwright** | Cover the critical web journeys. Run in a dedicated CI job (for example, on main or before a release), not on every build. |
| Load and SLO tests | **k6** | Check documented SLO thresholds on a critical flow in a controlled environment, rather than in the fast pull-request test job. |

### CI checks and merge policy

Every push and pull request should build the application, run unit and integration tests, scan dependencies, and publish SonarQube analysis with JaCoCo coverage. A failed build or quality gate blocks merging. The team should define the quality-gate thresholds and k6 SLOs in project configuration/specification rather than relying on unspecified defaults. Playwright and k6 run as dedicated validation jobs, not on every build.

These are the selected tools; Maven plugins, CI jobs, and test profiles still need to be configured in the project.
