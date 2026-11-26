# Xray Cloud reporting — PT-82

## Setup

Enable Xray Cloud's Test and Test Execution issue types in project PT, and configure
Story as a requirement type. Create an Xray Cloud API key for an account allowed to
create Tests, Test Executions and requirement links. Store its values in GitHub
Actions secrets `XRAY_CLIENT_ID` and `XRAY_CLIENT_SECRET`. These are Xray credentials,
not an Atlassian API token. Do not put credentials in files, logs or comments.

## CI reporting

The workflow archives backend Surefire/Failsafe, Playwright Failsafe and simulator
pytest JUnit reports. The final `xray` job waits for all three producers and the
`xray-tools` tests, merges their XML with `scripts/xray_report.py`, then invokes
`scripts/xray_upload.py` once. Authentication uses the Cloud REST v2 endpoint;
one JUnit import creates one Test Execution per workflow run. Retrying the upload
job can create another execution, so check Xray before retrying a network timeout.

Uploads run on pushes to dev/main and the PT-82 verification branch, and on
workflow_dispatch. Ordinary pull requests run tests and archive reports without
using Xray credentials or creating executions. The PT-82 branch push allows a real
pre-merge check because GitHub exposes manual dispatch only after the workflow is
present on the default branch. The e2e job runs for these same upload events.

Failed test outcomes are preserved and uploaded when reports exist; the original
failed jobs keep CI red. Missing producer artifacts, empty reports, authentication
errors and import errors fail reporting. Browser runs archive only Failsafe reports,
so their repeated unit tests are not imported twice. Cucumber results already in
Failsafe are not imported again from raw Cucumber output.

The `xray-report` artifact contains the merged XML. The upload log and job summary
record the returned Test Execution key. Open that issue in Jira to inspect runs.

## Story links

`scripts/xray-requirements.json` maps test class/report patterns to Jira requirements.
The merger also loads `scripts/xray-*-requirements.json` extensions. Hub mappings
are introduced by PT-33 in `scripts/xray-hub-requirements.json`, so either PR can
merge first without including mappings for absent hub classes in PT-82.

The merged XML adds the enhanced JUnit testcase property `requirements`, for example
`<property name="requirements" value="PT-34"/>`. Xray uses it to associate the
imported Generic Test with the story. Existing `test_key` properties are preserved;
a story key belongs in `requirements`, not `test_key`. Keep class and test names
stable so subsequent imports reuse Generic Tests. Long Cucumber names distinguish
scenario outline examples. Gherkin tags alone do not provide JUnit requirement links.

Cucumber tests imported this way are Generic Tests: this workflow does not import
native Cucumber step results. In Jira, inspect a mapped Test's covered requirements
and the story's Xray Test Coverage panel to verify the association. For manual
Tests, add the Test through that panel.

## Local validation

```sh
python -m unittest discover -s scripts/tests -v
python scripts/xray_report.py --reports backend/target/surefire-reports --output /tmp/xray.xml
```

The uploader also accepts optional `XRAY_TEST_PLAN_KEY` and `XRAY_TEST_EXEC_KEY`
environment variables for authorized manual use; CI creates a new execution.

## Troubleshooting

- Skipped upload: ordinary PRs do not upload; use a supported push or manual run.
- HTTP 401/403: verify Xray credentials and account permissions.
- HTTP 400: verify PT issue types and required Jira fields.
- Missing coverage: inspect the merged testcase requirements and Xray requirement types.
- Missing reports: inspect the producer's test and artifact steps.

## References

- [Enhanced JUnit requirements](https://getxraydocs.atlassian.net/wiki/spaces/XRAYCLOUD/pages/44564745)
- [Cloud REST v2 imports](https://getxraydocs.atlassian.net/wiki/spaces/XRAYCLOUD/pages/44577176)
