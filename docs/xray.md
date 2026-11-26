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

   Do not paste credentials into Jira comments, commits or workflow files.

## Link tests to user stories

For a manual test, create an issue of type **Test**. For an automated test, first
let CI import it, then select the resulting Generic Test. Open story **PT-33** and use
its Xray **Test Coverage** section → **Add Tests** → choose the test. Depending on
Jira's screen configuration, this can instead be done from the Test's covered
requirements section. Verify PT-33 appears as a covered requirement.

The CI uploads the original Maven JUnit XML directly. `@Tag("PT-33")`, a
`@PT-33` feature tag or a story key in a test title alone does not add a requirement
link through this JUnit import. Link the imported Tests to PT-33 in Xray as above.
Subsequent imports match Generic Tests by class and test name, so keep names stable.
`junit-platform.properties` uses long Cucumber names to distinguish outline examples.

For optional automatic linking, Xray also accepts a `requirements` testcase property:

```xml
<properties><property name="requirements" value="PT-33"/></properties>
```

The optional `scripts/xray_report.py` utility can add these properties using
`scripts/xray-requirements.json`; the direct-upload workflow does not call it.
An existing Generic Test can also be identified by its real `test_key` property.
A story key belongs in `requirements`, never in `test_key`.

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

The backend step immediately after `./mvnw -B verify` uses Bash, curl and jq on
`ubuntu-latest`, following the direct REST API approach supplied for the course.
It runs on pushes to `dev` / `main`, including when preceding tests fail, provided
the workflow has not been cancelled. Pull requests and manual runs do not upload.

1. Collect `target/surefire-reports/TEST-*.xml` and `target/failsafe-reports/TEST-*.xml`.
2. Authenticate at `/api/v2/authenticate` with the two GitHub secrets.
3. Mask the returned token in the Actions log.
4. POST each XML separately to `/api/v2/import/execution/junit?projectKey=PT`.

The project key is set directly to `PT` in the step. `XRAY_ENABLED`,
`XRAY_PROJECT_KEY` and `XRAY_TEST_PLAN_KEY` repository variables are not needed.
Each report import creates its own Test Execution; this version does not combine
reports into a single execution. The API response with the execution key appears
in the step log. Open that execution in Jira and check the test statuses, then
associate the imported Tests to their stories through **Test Coverage**.

Only backend reports are sent to Xray. Frontend, simulator and raw Cucumber reports
remain downloadable as GitHub Actions artifacts. Cucumber results already included
in Failsafe are not uploaded a second time from `target/cucumber`.

Missing reports, authentication failures and upload failures fail the step.
Failed tests keep CI red even if uploading succeeds. No real upload has been
validated until the team configures its credentials and runs CI on GitHub.

## Optional local utilities

The previous report preparation and Python upload utilities remain available for
manual use; they are not part of the direct-upload CI step:

```sh
python -m unittest discover -s scripts/tests -v
python scripts/xray_report.py --reports backend/target/surefire-reports --output /tmp/xray.xml
```

For an authorized local upload, set `XRAY_CLIENT_ID`, `XRAY_CLIENT_SECRET` in the
environment and run `python scripts/xray_upload.py /tmp/xray.xml`. That optional
script also accepts `XRAY_PROJECT_KEY`, `XRAY_TEST_PLAN_KEY`, `XRAY_TEST_EXEC_KEY`.

## Troubleshooting

- **Step skipped:** check the event is a push rather than a PR or manual run.
- **401/403:** check Xray Cloud credentials and the associated user's permissions.
- **400:** check project issue types, required Jira fields and the project key.
- **No coverage:** link the imported Generic Tests to their stories in Xray.
- **No reports:** inspect the original backend build/test step.

## References

- [Enhanced JUnit XML and requirement properties](https://getxraydocs.atlassian.net/wiki/spaces/XRAYCLOUD/pages/44564745)
- [Import execution results, Cloud REST v2](https://getxraydocs.atlassian.net/wiki/spaces/XRAYCLOUD/pages/44577176)
- [Cucumber feature import and requirement tags](https://getxraydocs.atlassian.net/wiki/spaces/XRAYCLOUD/pages/44577091)
