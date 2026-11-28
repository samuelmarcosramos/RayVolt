---
name: frontend
description: Use for any change in RayVolt's frontend (React + TypeScript in frontend/src), to build a page or component or to check existing frontend code. Keeps the structure of AGENTS.md section 5 (api/ services, one folder per feature, reusable components only when reused, CSS variables) so the UI stays reusable without turning into many tiny files.
---

# Build or check frontend code

The rules are in AGENTS.md section 5 ("Frontend"). Read them first. This file only gives the steps.

## Before writing

1. Read the story and its AC, and AGENTS.md sections 5 and 7.
2. Look at what exists: `frontend/src/api/`, `components/reusable/`, the feature folders, `utils/`, `index.css`.
3. Decide, and tell the human before coding:
   - which feature folder the change goes in (an existing one, or a new one named after the feature);
   - which existing reusable components and `api/` functions you reuse;
   - if a part becomes a new reusable component: where else it is used. Used once = it stays in the feature.

## Writing

- Backend calls: add a function to the matching `api/<thing>Service.ts` (or a new service file), with a type for the response. Components call these functions, never `fetch` directly.
- New colours: add a purpose-named variable in `index.css` for both themes, then use it.
- Keep a page in one file with its CSS until a part is really used elsewhere.
- Only user-facing text and labels that a Playwright test can find (`getByRole`, `getByLabel`, `getByText`).

## Check

1. `cd frontend && npm run build && npm run lint`. Both must pass.
2. List any rule of section 5 that the change does not follow, and why.

## Checking existing code

When asked to check instead of build, report a numbered list: file, rule from section 5 it breaks, suggested fix. Change nothing unless the human asks.
