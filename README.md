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

## SQA strategy and selected tools (Quality Assurance & Testing Toolset)

The strategy follows the assignment's required practices while keeping the pull-request feedback loop practical.

| Layer / Phase | Tools & Stack | Purpose Pipeline Policy |
| :--- | :--- | :--- |
| **Static Analysis & Coverage (`Q1`, `Q2`)** | SonarQube + JaCoCo | Triggered on every push and PR. Enforces quality gates on new code and blocks merges if thresholds fail. |
| **Dependency Security (`Q3`)** | Dependabot | Continuously audits project dependencies for CVEs and automatically opens PRs with version updates via `.github/dependabot.yml`. |
| **Code Review (`Q4`)** | GitHub Pull Requests | Branch protection enforced on `main`. Requires at least one peer approval and passing status checks before merging. |
| **AI Code Review (`AI3`)** | GitHub Copilot | Automated PR feedback on every pull request. A human reviewer always holds the final merge decision and logs accepted/rejected suggestions. |
| **Unit Testing (`T2`)** | JUnit 6 (Jupiter) + Mockito + AssertJ | Fast, isolated tests targeting core domain logic (`*Test`). Executed on every commit via Maven Surefire (`mvn test`). |
| **BDD / Acceptance (`T1`)** | Cucumber 8 + Gherkin | Translates user-story acceptance criteria into executable specifications (`Given/When/Then`) running on the JUnit Platform. |
| **Integration Testing (`T3`)** | Spring Boot Test + Testcontainers | Isolated from unit tests (`*IT`). Validates REST contracts and persistence against a real PostgreSQL instance via Maven Failsafe (`mvn verify`). |
| **E2E Web UI Testing (`T4`)** | Playwright | Executed on PRs targeting `main` or scheduled nightly runs. Verifies critical end-to-end user journeys in a real browser. |
| **Load & SLO Testing (`T5`)** | k6 | Executed in a controlled staging environment (outside standard PR builds) to validate latency and throughput against defined SLOs. |
| **Accessibility Audit (`T8`)** | Lighthouse | Runs against the deployed web interface to ensure compliance with minimum accessibility score thresholds. |
| **Test Management (`PM5`)** | Jira + Xray | Links automated JUnit/Cucumber runs and manual exploratory charters directly to Jira stories for real-time requirement traceability. |
| **Continuous Integration (`D2`)** | GitHub Actions | Automates build, test execution, and SonarQube checks on every push/PR, reporting build status via README badges and failure alerts. |
| **Infrastructure as Code (`D3`)** | Docker Compose | Defines and orchestrates the full application stack (backend, frontend, PostgreSQL database, and supporting services) reproducibly. |
| **Continuous Delivery (`D4`)** | GitHub Actions → VM | Automatically builds immutable container images and deploys every merge to `main` into the target environment with seeded data. |
| **Observability (`D6`)** | OpenTelemetry + Grafana Stack | Collects metrics, structured logs, and distributed traces from production to monitor service health and error budgets. |

### Key Practices Enforced in the Pipeline

* **Shift-Left & Fast Feedback:** Fast unit tests and static analysis run on every push (`< 10 min`); heavier integration, BDD, and E2E suites run in subsequent pipeline stages.
* **Branch Protection:** Direct pushes to `main` are blocked; pull requests require passing CI builds, a green SonarQube Quality Gate, and peer code review.
* **Traceability & Living Documentation:** Gherkin scenarios and automated JUnit/Cucumber execution reports are synced to Xray in Jira to continuously track requirement coverage (`OK` / `NOK`) and release readiness.
