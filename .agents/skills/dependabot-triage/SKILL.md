---
name: dependabot-triage
description: Use when asked to check or triage the open Dependabot pull requests in RayVolt. Classifies each update, runs the tests of major updates on their branch, and reports a table with a merge or close verdict following the team's Dependabot merge rule. A human merges or closes; the skill never does.
---

# Triage Dependabot PRs

The merge rule is in the QA Manual, section 2.4 (Quality Gates & Dependency Management):

- minor or patch update (x.Y.Z): merge when CI is green;
- major update: test it on its branch before merging;
- majors that leave the runtime versions (Java 21, Node 24, e.g. temurin, node, @types/node): close;
- security updates opened against `main`: close, and apply the same fix through a PR into `dev`.

## Steps

1. List the open Dependabot PRs:
   `gh pr list --author app/dependabot --state open --json number,title,baseRefName,headRefName,labels`
2. For each PR read `gh pr view <n>` and `gh pr checks <n>`. Classify it:
   - kind: patch, minor, major or security (security = based on `main`, or Dependabot says it fixes an alert);
   - ecosystem: maven, npm, pip, docker or github-actions.
3. For each **major** that is not closed by the rule, test it in a separate worktree, never the human's working copy:
   ```
   git fetch origin <headRefName>
   git worktree add ../rayvolt-dependabot-<n> origin/<headRefName>
   ```
   Run what the update touches:
   - maven: `cd backend && ./mvnw verify` (and `./mvnw -Pe2e verify` after `cd ../frontend && npm ci && npm run build` for Playwright updates);
   - npm: `cd frontend && npm ci && npm run build && npm run lint`;
   - pip / python image: `cd simulator && pip install -r requirements-dev.txt && pytest` (in the new image for a docker update);
   - docker: `docker compose build <service>`;
   - github-actions: read the release notes for breaking changes (cannot run locally).
   Then `git worktree remove ../rayvolt-dependabot-<n>`.
4. Check whether several PRs bump the same library (they must be merged together).

## Limits

- Never merge, close, approve or comment on a PR.
- Never push to a Dependabot branch.

## Output

| PR | Update | Kind | CI | Tested by agent | Verdict | Why |
|---|---|---|---|---|---|---|
| #52 | jackson 2.19.1 → 2.19.2 | patch | green | not needed | Merge | patch, CI green |
| #53 | playwright 1.65 → 2.0 | major | green | `-Pe2e verify` passed | Merge | major, tested |
| #54 | node 24 → 26 | major | – | – | Close | leaves Node 24 |

Under the table: PRs to merge together, and any failure with its log lines.
