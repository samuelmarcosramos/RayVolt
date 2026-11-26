"""Upload enhanced JUnit XML to Xray Cloud; credentials come only from environment."""
import argparse
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = 'https://xray.cloud.getxray.app/api/v2'


def post(path, body, content_type, token=None):
    headers = {'Content-Type': content_type}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    request = urllib.request.Request(BASE_URL + path, data=body, headers=headers, method='POST')
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def upload(report):
    credentials = {key: os.environ.get('XRAY_' + key.upper(), '') for key in ('client_id', 'client_secret')}
    if not all(credentials.values()):
        raise ValueError('Set XRAY_CLIENT_ID and XRAY_CLIENT_SECRET in GitHub Actions secrets')
    token = post('/authenticate', json.dumps(credentials).encode(), 'application/json')
    if not isinstance(token, str) or not token:
        raise ValueError('Xray authentication returned no token')
    query = {'projectKey': os.environ.get('XRAY_PROJECT_KEY') or 'PT'}
    for variable, parameter in [('XRAY_TEST_PLAN_KEY', 'testPlanKey'), ('XRAY_TEST_EXEC_KEY', 'testExecKey')]:
        if os.environ.get(variable):
            query[parameter] = os.environ[variable]
    result = post('/import/execution/junit?' + urllib.parse.urlencode(query), report.read_bytes(), 'text/xml', token)
    execution = result.get('testExecIssue', {}).get('key') or result.get('key')
    if not execution:
        raise ValueError('Xray response contained no Test Execution key')
    return execution


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    args = parser.parse_args()
    try:
        execution = upload(args.report)
    except urllib.error.HTTPError as error:
        raise SystemExit(f'Xray import failed with HTTP {error.code}; check API permissions and project configuration') from None
    except (ValueError, OSError) as error:
        raise SystemExit(str(error)) from None
    print(f'Xray Test Execution: {execution}')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write(f'Xray Test Execution: **{execution}**\n')
