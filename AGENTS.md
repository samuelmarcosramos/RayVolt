# AGENTS.md

Instructions for AI agents (and humans) working in this repository. They must stay consistent with the QA Manual (`docs/`).

## 1. Project

RayVolt runs a fleet of shared e-scooters for Valdren Works, a large industrial complex (workers moving between departments, security guards on patrol rounds).
All names are fictional.

| Folder | What | Stack |
|---|---|---|
| `backend/` | REST API, business rules, persistence | Java 21, Spring Boot, Maven, PostgreSQL |
| `frontend/` | Web UI | React, npm |
| `simulator/` | Telemetry simulator (separate container) | Python, paho-mqtt |
| `broker/` | MQTT broker config | Mosquitto |
| `docs/` | Reports (PDF at each tag) | |
| `.github/` | Workflows, PR template | GitHub Actions |

Backlog: Jira project `PT`. Every change belongs to a Jira item `PT-<n>`.

## 2. Build and test commands

```
backend/    ./mvnw test        # unit + slice + Cucumber tests, fast, no Docker
            ./mvnw verify      # + integration tests (*IT: Testcontainers, Playwright) + JaCoCo report
frontend/   npm ci && npm test && npm run build
simulator/  pip install -r requirements-dev.txt && pytest
root        docker compose up --build   # whole system locally
```

## 3. Git flow

- One branch per Jira item: `<type>/PT-<n>-short-name`, type = `feature`, `fix` or `chore`.
- Commit messages start with the key: `PT-<n>: what changed`.
- Pull requests go into `dev`, with `PT-<n>` in the title. Never push directly to `dev` or `main`.
- `dev` is merged into `main` at the end of each iteration and tagged (`iter1` … `iter5`, `final_v1`).

## 4. Test rules

- Pick the test level per behaviour:
  - business rules (booking window, battery >= 10%, fees) → plain unit tests (JUnit 5, AssertJ, Mockito);
  - HTTP contract (status codes, JSON, validation) → `@WebMvcTest` with the service mocked;
  - queries → `@DataJpaTest` against PostgreSQL with Testcontainers;
  - main user journeys → a few end-to-end tests (`@SpringBootTest` + Playwright).
- Tests that need Docker or a browser are named `*IT` and run in `mvn verify` (Failsafe), so `mvn test` stays fast.
- Acceptance criteria of core stories are Gherkin scenarios automated with Cucumber, tagged with the story key (`@PT-<n>`).
- Keep automated tests traceable to their Jira stories, so that CI test results can be reported to Xray.
- Test boundaries (exactly at the limit, just before, just after), not only one value in the middle.
- Assertions must be able to fail and must name the rule (`assertEquals` / AssertJ, not `assertTrue`).
- Browser tests: user-facing locators (`getByRole`, `getByLabel`, `getByText`), no `Thread.sleep`.
- Make time deterministic with a fixed `Clock`.
- Only synthetic test data.

## 5. Code style

- English everywhere: code, comments, commits, PRs, Jira.
- Small classes and methods, clear names over comments. Few, short comments only where the reason is not obvious.
- Fix SonarQube issues on new code before asking for review.

## 6. Definition of done

A story is done only when all of these are true:

1. It is on a `<type>/PT-<n>-...` branch with a PR into `dev` that has `PT-<n>` in the title.
2. Every acceptance criterion has at least one automated test (any level), listed in the PR's AC → test table. For core stories the AC are Gherkin scenarios automated with Cucumber.
3. Each behaviour is tested at the right level (see section 4).
4. CI is green: build and all tests pass.
5. The SonarQube quality gate passes (coverage on new code >= 75%, no new issues, security hotspots reviewed).
6. At least 1 teammate who is not the author approved the PR. AI review findings are answered (accepted or rejected, with the reason).
7. No secrets, credentials or personal data; only synthetic test data.
8. Docs are updated: API docs if an endpoint changed, this file / the QA Manual if a rule changed.
9. The change works with `docker compose up`.
10. The Jira item is moved to Done after the merge.

## 7. Rules for AI agents

- Before implementing a change, read the related Jira story, its acceptance criteria and the existing code.
- Keep backend code organized by domain module, with `controller`, `service`, `model` and `repository` inside each module. Shared configuration and error handling stay outside domain modules.
- Fleet lifecycle rules are specified by PT-34 and `backend/src/test/resources/features/fleet/scooter_lifecycle.feature`. Persisted state changes go through `fleet.service.FleetService.changeStatus`, so the state and its timestamped cause are saved atomically. Other modules must call this service instead of writing scooter status directly.
- TODO when Bookings & Trips, Maintenance and Telemetry are integrated: connect reservation/cancellation/expiry, trip, ticket and telemetry events to this Fleet lifecycle service with the corresponding cause. Operator authorization remains a Users dependency.
- Follow the existing architecture and module responsibilities. Do not change architectural boundaries without justification
  (e.g. the backend talks to the simulator only through the MQTT broker, and only the MQTT adapter knows MQTT).
- Do not add dependencies with known security vulnerabilities, and respect the dependency security checks in CI.
- A PR description written by an agent follows `.github/pull_request_template.md`: it starts with the Jira item `PT-<n>`
  and the AC → test table, with each acceptance criterion copied from Jira and the test that covers it.
  If the item has no AC, the agent stops and asks instead of guessing.
- Agents never merge. A human approves every pull request and is accountable for it.
- Never send secrets, credentials or personal data to an AI service.
- Every merged line must be explainable by a team member at a checkpoint.
- AI review findings on a PR are recorded as accepted or rejected, with the reason.
- Follow this file. If a rule here seems wrong, say so in the PR instead of ignoring it.
