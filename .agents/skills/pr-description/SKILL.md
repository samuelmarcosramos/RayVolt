---
name: pr-description
description: Use when asked to write a pull request title and description for RayVolt. Fills .github/pull_request_template.md for the current branch (Jira key, AC → test table copied from Jira, what changed, AI use, Agent flow for AI4 stories, how to test). Gives the text to the human, who opens the PR.
---

# Write a PR description

The rules are in AGENTS.md sections 3 and 7. This file only gives the steps.

## Input

- The branch (default: the current one) and its Jira key `PT-<n>` (from the branch name).
- The story and its acceptance criteria. If you cannot read Jira, ask the human to paste them. If the story has no AC, stop and ask (section 7).

## Steps

1. Read the changes: `git log --format='%h %s%n%b' origin/dev..HEAD` and `git diff origin/dev...HEAD`.
2. Read `.github/pull_request_template.md` as it is on the branch (`HEAD`), because the CI check uses the template of the PR's own code, and keep **every `## ` section, with the exact heading**. The `pr-description` CI check fails if one is missing.
3. Fill it:
   - **Title**: `PT-<n>: <what changed>`.
   - **Jira**: `PT-<n>`.
   - **AC → test**: one row per AC, copied word for word from Jira, with the test that covers it (class and method). If the test author's handoff has the AC → test table, start from it. An AC without a test gets "none" and you tell the human.
   - **What changed**: short bullets of what a reviewer needs to know, not a list of every file.
   - **AI use**: tool and what it did, from the human and the commit messages. Ask the human if you do not know. AI review findings stay "0 accepted, 0 rejected" until the review.
   - **Agent flow**: only if the branch has commits ending in `(implementer)` / `(test author)`. One row per step with role, tool, commit hash, and who approved the checkpoint (ask the human). Otherwise delete the `###` part.
   - **How to test**: commands and what the reviewer should see.
   - **Reviewer checklist**: leave every box empty. Agents never tick it (section 7).
4. Check the AC → test table against the code: every listed test exists.

## Limits

- Never run `gh pr create`, `gh pr edit` or comment. Give the text, the human opens the PR.
- No AI co-author or "generated with" lines.
- Write as the PR author, in the first person ("me", "I"). Never use a team member's name for the author.

## Output

The title on one line, then the description in one markdown block, ready to paste.
