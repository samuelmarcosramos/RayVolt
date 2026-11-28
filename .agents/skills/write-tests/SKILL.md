---
name: write-tests
description: Test author role for RayVolt. Use when asked to write tests for a Jira story (PT-<n>), as the second step of the AI4 flow or on its own. Writes tests from the acceptance criteria, at the right level, without changing production code. Reports failing behaviour instead of fixing it.
---

# Write tests for a story

You are the **test author** (AGENTS.md section 9). Read AGENTS.md first and follow it. The test rules are in section 4, this file only gives the steps.

Write the tests from the **acceptance criteria**, not from the code. The question is "does the code do what the AC say", not "what does the code do".

## Input

- The Jira key `PT-<n>` and its acceptance criteria. If you cannot read Jira, ask the human to paste them.
- In the AI4 flow: the `.feature` file and the implementer's handoff note (`git log` on the branch, commit ending in `(implementer)`). Do not read the implementer's chat.

Stop and ask if the story has no AC.

## Steps

1. Read the AC, AGENTS.md section 4, and the existing tests next to the code you test (`backend/src/test/java/com/rayvolt/backend/<layer>/`). Copy their style.
2. For each AC, pick the level (section 4): business rule → unit test, HTTP contract → `@WebMvcTest` with the service mocked, query → repository test against PostgreSQL (`*IT`, see `PostgresHubRepositoryIT`), main journey → E2E only if no E2E covers it yet.
   The Cucumber scenarios already cover the AC end to end. Add the tests that check the rules in detail (boundaries, errors, invalid input), not a second copy of the scenarios.
3. Write the tests:
   - tag each test class with `@Tag("PT-<n>")` so CI results reach Xray;
   - test boundaries: exactly at the limit, just before, just after;
   - assertions that can fail and name the rule (AssertJ / `assertEquals`, not `assertTrue`);
   - fixed `Clock` for time, synthetic data only;
   - clean the data in `@BeforeEach`, not after.
4. Check each test can fail: break the assertion or the input on purpose, run it, see it fail, then undo.
5. Run `cd backend && ./mvnw verify`. Everything must pass, including the JaCoCo check, except tests that fail because the code is wrong (see Limits).

## Limits

- Change only test files. Never production code, the `.feature` file or the step definitions.
- If a test fails because the code is wrong, keep the test, do not "fix" it to pass, and report it in the handoff. The work goes back to the implementer.
- Never push, open, merge or comment on a PR.

## Handoff

Stage your changes and show the human:

1. A table: AC | test (class and method) | level. Every AC has at least one row. This table is reused in the PR's AC → test table.
2. The commit message, with the handoff note in the body:

```
PT-<n>: <what is tested> (test author)

Done: <tests added, per AC>
Failing: <tests that fail because of the code, and why> (or "none")
Run: <commands>
```

Commit only when the human approves. That approval is the checkpoint after the test author step.
If some tests fail because of the code, do not commit. The human sends the failing tests to the implementer, and you commit once they pass.
