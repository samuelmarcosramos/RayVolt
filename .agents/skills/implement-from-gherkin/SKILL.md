---
name: implement-from-gherkin
description: Implementer role for AI4 stories in RayVolt. Use when asked to implement a Jira story (PT-<n>) from its Gherkin .feature file. Writes the production code and the Cucumber step definitions until the scenarios pass, then stops for the human checkpoint. Does not write the other tests and never opens or merges a PR.
---

# Implement a story from its Gherkin scenarios

You are the **implementer** in the AI4 flow (AGENTS.md section 9). Read AGENTS.md first and follow it. The rules are there, this file only gives the steps.

## Input

- The Jira key `PT-<n>`.
- The story and its acceptance criteria. If you cannot read Jira, ask the human to paste them.
- The `.feature` file, in `backend/src/test/resources/features/<area>/`, tagged `@PT-<n>`.

Stop and ask the human if any of these is missing, or if a scenario does not match an acceptance criterion. Do not guess.

## Steps

1. Read the story, the AC, the `.feature` file and AGENTS.md sections 4, 5, 7 and 9.
2. Read the existing code the story touches. The backend is organized by layer under `com.rayvolt.backend` (`controller`, `service`, `repository`, `model`, `config`, `errors`, `mqtt`). Reuse what exists before adding classes.
3. If the feature has no runner yet, add one in `backend/src/test/java/com/rayvolt/backend/acceptance/`, named `<Name>AcceptanceIT`, copying an existing runner (same glue, report files `target/cucumber/PT-<n>.json` and `.xml`).
4. Run the feature to get the undefined step snippets:
   `cd backend && ./mvnw verify -Dit.test=<Name>AcceptanceIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
5. Write the step definitions in `acceptance/` (`<Name>Steps`), then the production code, until every scenario passes. Work outside-in: controller, then service, then repository.
6. Run the whole build: `cd backend && ./mvnw verify`. Everything must pass. If the change touches the frontend, also `cd frontend && npm run build`.

## Limits

- Do not change the `.feature` file. If a scenario looks wrong, stop and tell the human.
- Do not write or change other tests (unit, `@WebMvcTest`, `@DataJpaTest`, E2E). That is the test author's job.
- Do not add a dependency unless the human agrees.
- Never push, open, merge or comment on a PR.

## Handoff

Stage your changes and show the human:

1. A short summary of what changed (file and what).
2. The commit message, with the handoff note in the body:

```
PT-<n>: <what changed> (implementer)

Done: <what was implemented, per scenario>
Unsure: <assumptions, open questions, anything the test author should look at>
Run: <commands to build and run the scenarios>
```

Commit only when the human approves. That approval is the checkpoint after the implementer step.
