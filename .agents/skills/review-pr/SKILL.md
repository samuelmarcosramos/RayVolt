---
name: review-pr
description: Reviewer role for RayVolt. Use when asked to review a pull request (by number or branch), as the last step of the AI4 flow or on any PR. Checks the PR against the acceptance criteria, the definition of done and the reviewer checklist, and returns a numbered list of findings for a human to accept or reject. Changes no files and never comments on, approves or merges the PR.
---

# Review a pull request

You are the **reviewer** (AGENTS.md section 9). Read AGENTS.md first. The definition of done is section 6, the reviewer checklist is in `.github/pull_request_template.md`. This file only gives the steps.

You give findings to a human. The human decides, posts them on the PR and records each one as accepted or rejected (AGENTS.md section 7).

## Input

- The PR number, or the branch.
- The Jira story and its acceptance criteria. If you cannot read Jira, use the AC → test table in the PR and say so in the report.

## Steps

1. Read the PR without changing anything:
   `gh pr view <n>`, `gh pr diff <n>`, `gh pr checks <n>`.
2. Read the code around the changes, not only the diff.
3. Check, in this order:
   1. **AC**: every acceptance criterion is in the AC → test table, and the listed test really checks it (it would fail if the AC were broken).
   2. **Correctness**: bugs, missing cases, wrong boundaries, concurrency, error handling.
   3. **Tests**: right level (section 4), assertions that can fail, boundaries, no `Thread.sleep`, synthetic data, `@Tag("PT-<n>")`.
   4. **Rules**: architecture boundaries and layers (section 7), code style (section 5), frontend structure for frontend changes.
   5. **Definition of done** (section 6) and every item of the reviewer checklist.
   6. **PR description**: follows the template, "AI use" filled, "Agent flow" filled for AI4 stories.
4. If you need to run the tests, use a separate worktree (`git worktree add`), never the human's working copy, and remove it after.

Only report what you checked and are sure of. If you are not sure, say so in the finding. Do not report style taste that no rule asks for.

## Limits

- Change no files.
- Never comment on, approve, request changes on or merge the PR.

## Output

A numbered list, most important first:

```
1. [bug | AC | test | rule | DoD | description] file:line
   Problem: what is wrong
   Why: the rule or AC it breaks, or the input that makes it fail
   Fix: what to change
```

Then one line per reviewer checklist item: ok / not ok (finding number).
End with: "Findings to accept or reject: 1..N".

If you found nothing, say so and list what you checked.
