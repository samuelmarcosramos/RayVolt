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
| `.agents/skills/` | Agent skills (see section 8) | `SKILL.md` per skill |

Backlog: Jira project `PT`. Every change belongs to a Jira item `PT-<n>`.

## 2. Build and test commands

```
backend/    ./mvnw test        # unit + slice tests, fast, no Docker
            ./mvnw verify      # + integration tests (*IT: Testcontainers) + JaCoCo report
            ./mvnw -Pe2e verify  # Playwright E2E tests (e2e/), CI runs them only on push to dev/main
                               # they need the frontend built first (frontend/: npm run build)
frontend/   npm ci && npm run build
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

Frontend (`frontend/src/`):

- Calls to the backend live only in `api/` (`<thing>Service.ts`), never inline `fetch` in a component.
- One folder per feature in `components/` (`layout/`, `home/`, `fleet/`, ...), each with its own CSS file.
- A component goes to `components/reusable/` only when it is used in more than one place. Before writing a new component, check if one in `reusable/` already does the job.
- Do not split a page into many tiny files. A small part used once stays in its feature file.
- Pure helpers (formatting, sorting, filtering) go in `utils/`.
- Colours are CSS variables in `index.css`, named by purpose (`--primary`, `--surface`, `--danger`, ...), with a light and a dark theme. No hard-coded colours in components. `App.css` holds the app shell and styles shared by features.
- Frontend behaviour is tested with Playwright browser tests in the backend (`e2e/`, section 4), not with frontend unit tests.

## 6. Definition of done

A story is done only when all of these are true:

1. It is on a `<type>/PT-<n>-...` branch with a PR into `dev` that has `PT-<n>` in the title.
2. Every acceptance criterion has at least one automated test (any level), listed in the PR's AC → test table. For core stories the AC are Gherkin scenarios automated with Cucumber.
3. Each behaviour is tested at the right level (see section 4).
4. CI is green: build and all tests pass.
5. The SonarCloud quality gate (Sonar way) passes: coverage on new code >= 80%, duplicated lines on new code <= 3%, A ratings on new code, security hotspots reviewed.
6. At least 1 teammate who is not the author approved the PR. AI review findings are answered (accepted or rejected, with the reason).
7. No secrets, credentials or personal data; only synthetic test data.
8. Docs are updated: API docs if an endpoint changed, this file / the QA Manual if a rule changed.
9. The change works with `docker compose up`.
10. The Jira item is moved to Done after the merge.

## 7. Rules for AI agents

- Before implementing a change, read the related Jira story, its acceptance criteria and the existing code.
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
- Every PR fills the "AI use" section of the template: which tool, and what it did (code, tests, review, docs), or "none".
- Follow this file. If a rule here seems wrong, say so in the PR instead of ignoring it.

## 8. Agent skills

Skills live in `.agents/skills/<name>/SKILL.md` (read by Codex and Copilot). `.claude/skills` is a link to the same folder for Claude Code.
A skill only holds the steps of a workflow. The rules stay in this file and the skill points here instead of repeating them.

| Skill | What it does | When |
|---|---|---|
| `implement-from-gherkin` | Reads the story's AC and `.feature` file, writes the production code and step definitions, stops before the PR | AI4 stories (implementer role) |
| `write-tests` | Writes the tests for each AC at the right level (section 4) | AI4 stories (test author role), optional elsewhere |
| `review-pr` | Checks a PR against the definition of done and the reviewer checklist, lists findings for a human to accept or reject | AI4 stories (reviewer role), optional elsewhere |
| `frontend` | Builds or checks frontend code against the frontend structure: API calls in `src/api/`, one folder per feature with its own CSS, a component goes to `src/components/reusable/` only when it is used in more than one place | Any frontend change |
| `pr-description` | Fills `.github/pull_request_template.md`, with the AC → test table copied from Jira | Any PR |
| `dependabot-triage` | Reports a table of the open Dependabot PRs (update, kind, CI, tests run, verdict merge / close, reason) following the Dependabot merge rule in the QA Manual. Runs the tests of major updates itself | Weekly, when Dependabot PRs arrive |
| `ai-log` | Reads the merged PRs and their "AI use" section and writes `docs/ai-log.md` (share of AI work, AI findings accepted / rejected, rework traced to AI) | At each iteration tag |

Skills report and propose. They never merge, close or comment on a PR (section 7).

## 9. Agent roles for AI4 stories

AI4 stories (built from their specification by agents) use three agents with fixed roles. Each agent is a new session that does not see the previous one's chat,
only what is in the branch. A human checks the work between every step. Other stories may use single skills but do not need this flow.

| Role | Skill | May change | Must not change |
|---|---|---|---|
| Implementer | `implement-from-gherkin` | production code, step definitions | the `.feature` file, tests written by the test author |
| Test author | `write-tests` | test files | production code (failing behaviour is reported, not fixed) |
| Reviewer | `review-pr` | nothing | any file |

Flow:

1. A human writes the Gherkin `.feature` file from the Jira AC. Checkpoint: the scenarios match the AC.
2. Implementer writes the code and commits `PT-<n>: ... (implementer)`, with a handoff note in the commit body: what was done, what is unsure, how to run it.
   Checkpoint: a human reads the diff and runs the build.
3. Test author reads only the AC, the `.feature` file and the handoff note, writes the tests and commits `PT-<n>: ... (test author)` with a handoff note.
   If a test fails because of the code, the work goes back to step 2. Checkpoint: a human checks the tests can fail.
4. A human opens the PR. Its description has an "Agent flow" part: each step with role, tool, commit and who approved the checkpoint.
5. Reviewer lists findings: Copilot review on the PR, or the `review-pr` skill whose findings a human posts on the PR. Checkpoint: the human accepts or rejects each finding with the reason, a teammate approves and merges.


