# Xray Cloud setup and CI reporting — PT-82

## Initial Jira / Xray setup

1. Install/enable **Xray Test Management for Jira Cloud** on the Jira site. A Jira
   administrator must make Xray's Test, Test Execution and Test Plan issue types
   available to project `PT`, and enable Story as a requirement type in Xray's
   project settings. A normal Jira issue link alone is not Xray requirement coverage.
2. In Xray administration → **API Keys**, create a key for a team account allowed
   to create Test and Test Execution issues and link tests in project `PT`.
   Xray's **Client ID / Client Secret** are different from an Atlassian API token.
3. On GitHub → repository **Settings → Secrets and variables → Actions** add:

   | Type | Name | Value |
   |---|---|---|
   | Secret | `XRAY_CLIENT_ID` | Client ID from Xray |
   | Secret | `XRAY_CLIENT_SECRET` | Client Secret from Xray |
   | Variable | `XRAY_PROJECT_KEY` | `PT` |
   | Variable | `XRAY_ENABLED` | `true` |
   | Variable, optional | `XRAY_TEST_PLAN_KEY` | Key of a real Xray Test Plan |

   Do not paste credentials into Jira comments, commits or workflow files.

## Link tests to user stories

For a manual test: create an issue of type **Test**, open story **PT-33**, and use
its Xray **Test Coverage** section → **Add Tests** → choose the test. Depending on
Jira's screen configuration, this can instead be done from the Test's covered
requirements section. Verify PT-33 appears as a covered requirement.

For this CI implementation, no manual linking is required for mapped tests:
`scripts/xray-requirements.json` maps JUnit class/report patterns to story keys.
Frontend test titles can also contain the story key, for example
`PT-33 adds a hub`. The report preparer adds a testcase property:

```xml
<properties><property name="requirements" value="PT-33"/></properties>
```

Xray's enhanced JUnit importer creates the coverage link and matches automated
Tests by their generic definition (class + test name). Keep those names stable.
`junit-platform.properties` uses long Cucumber names so examples in different
scenario outlines have distinct test definitions.
To bind an existing Generic Test explicitly, emit a `test_key` property with its
real key; the preparer preserves it. A story key belongs in `requirements`, never
in `test_key`. Update the mapping whenever another story adds tests. Unmapped
infrastructure tests are imported without inventing a covered story.

Cucumber scenarios are executed as acceptance tests and their Failsafe JUnit
results are imported as **Generic** automated Tests by this workflow. Raw
Cucumber JSON/XML remain downloadable in `cucumber-results`. This gives coverage
and execution status; Xray will not display per-step Cucumber results through
this JUnit import.

If the team wants native **Cucumber** Tests with step-level evidence instead:
import the `.feature` files through Xray's feature importer, with `@PT-33` above
`Feature:` to link the requirement. Export the features from Xray, copy the real
Test issue key tags to the corresponding scenarios in the repository, then run
and import JSON using `/api/v2/import/execution/cucumber`. A story tag alone does
not identify a Cucumber Test. Use this as an alternative to importing the same
acceptance cases as Generic Tests, to avoid duplicate test definitions.

## Upload results from CI

- Pull requests build/test normally and archive reports, including failed tests.
- Pushes to `dev` / `main`, or **Actions → CI → Run workflow**, run the Xray job
  when `XRAY_ENABLED=true`. PR workflows do not upload results.
- Backend Surefire/Failsafe, frontend Vitest and simulator pytest results are
  downloaded, merged and enriched into the `xray-report` artifact.
- The job authenticates at `/api/v2/authenticate`, then sends the merged XML to
  `/api/v2/import/execution/junit?projectKey=PT`. Each run creates a Test Execution;
  the optional plan variable adds it to a Test Plan.
- The execution key appears in the job summary. Open that Test Execution in Jira,
  check PASS/FAIL, then open PT-33 → **Test Coverage** and inspect the covered Tests.
- Failed automated tests keep CI red and are still uploaded. Import/authentication
  failures also fail the Xray job. Tests that never started cannot produce results.

For local preparation without contacting Xray:

```sh
python -m unittest discover -s scripts/tests -v
python scripts/xray_report.py --reports backend/target/surefire-reports --output /tmp/xray.xml
```

The CI input directory includes both Surefire and Failsafe reports plus frontend
and simulator XML. Avoid feeding `target/cucumber/*.xml` as well: those duplicate
acceptance results already present in Failsafe.

For an authorized local upload, set `XRAY_CLIENT_ID`, `XRAY_CLIENT_SECRET` and
optionally `XRAY_PROJECT_KEY` / `XRAY_TEST_PLAN_KEY` in the environment, then run
`python scripts/xray_upload.py /tmp/xray.xml`. The script also accepts
`XRAY_TEST_EXEC_KEY` to update an existing Test Execution; CI deliberately creates
a fresh execution per run. Never print tokens to the console.

## Troubleshooting

- **Job skipped:** check `XRAY_ENABLED` is a repository variable equal to `true`,
  and that this is a push/manual run rather than a PR.
- **401/403:** verify Xray API credentials and the associated user's permissions.
- **400:** confirm issue types, required Jira fields and project key; extra required
  Test Execution fields may require Xray's multipart endpoint and Jira field IDs.
- **No coverage link:** ensure Story is configured as an Xray requirement type,
  the story key is correct, and `requirements` appears in the `xray-report` XML.
- **No reports:** inspect the original build/test job before retrying the upload.

## References

- [Enhanced JUnit XML and requirement properties](https://getxraydocs.atlassian.net/wiki/spaces/XRAYCLOUD/pages/44564745)
- [Import execution results, Cloud REST v2](https://getxraydocs.atlassian.net/wiki/spaces/XRAYCLOUD/pages/44577176)
- [Cucumber feature import and requirement tags](https://getxraydocs.atlassian.net/wiki/spaces/XRAYCLOUD/pages/44577091)
