---
name: ai-log
description: Use at each iteration tag, or when asked for AI usage data, in RayVolt. Reads the "AI use" section of the merged pull requests and writes docs/ai-log.md with the share of AI work, the AI review findings accepted and rejected, and rework traced to AI output. This is the data for the AI5 analysis in the QA Manual.
---

# Update the AI log

The rule that every PR fills "AI use" is in AGENTS.md section 7. This file only gives the steps.

## Steps

1. List the merged PRs into `dev`:
   `gh pr list --base dev --state merged --limit 200 --json number,title,author,mergedAt,body,url`
   Skip Dependabot PRs. If `docs/ai-log.md` already exists, only add PRs that are not in it yet.
2. From each PR's `## AI use` section read: tool, what it did (code, tests, review, docs), findings accepted and rejected.
   PRs without the section (older than the rule) are listed as "not recorded", not as "none".
3. From the `### Agent flow` table, mark the PR as AI4 and note the roles and tools.
4. Rework traced to AI: search the merged PRs for `fix` PRs or commits whose description or commit message says it fixes something an AI wrote, and list them with the PR they fix. Ask the human about any you are unsure of.
5. Write `docs/ai-log.md`:
   - a table, one row per PR: PR (link) | Jira | iteration | tool | what AI did | findings accepted / rejected | AI4;
   - a summary per iteration and in total: PRs with AI use / all PRs, findings accepted / rejected, number of AI4 stories, rework PRs traced to AI.
   Iteration = the tag (`iter1` … `iter5`) the merge date falls before (`git tag --list 'iter*'`, `git log -1 --format=%ci <tag>`).
6. Show the human the summary numbers and the PRs marked "not recorded".

## Limits

- Count only what the PRs say. Do not guess AI use for PRs that do not record it.
- Change only `docs/ai-log.md`. Never comment on PRs.
