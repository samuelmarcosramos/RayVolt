"""Upload enhanced JUnit XML to Xray Cloud; credentials come only from environment."""
import argparse
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = 'https://xray.cloud.getxray.app/api/v2'


def post(path, body, content_type, token=None, timeout=120):
    headers = {'Content-Type': content_type}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    request = urllib.request.Request(BASE_URL + path, data=body, headers=headers, method='POST')
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode('utf-8', errors='replace')
        secrets = [token, os.environ.get('XRAY_CLIENT_ID'), os.environ.get('XRAY_CLIENT_SECRET')]
        for secret in secrets:
            if secret and secret.strip():
                detail = detail.replace(secret.strip(), '[REDACTED]')
        endpoint = urllib.parse.urlparse(error.url).path
        detail = '\n'.join('  ' + line for line in detail.splitlines())
        raise ValueError(f'Xray {endpoint} failed with HTTP {error.code}. Response body:\n{detail}') from None


def upload(report):
    credentials = {key: os.environ.get('XRAY_' + key.upper(), '').strip() for key in ('client_id', 'client_secret')}
    if not all(credentials.values()):
        raise ValueError('Set XRAY_CLIENT_ID and XRAY_CLIENT_SECRET in GitHub Actions secrets')
    token = post('/authenticate', json.dumps(credentials).encode(), 'application/json')
    if not isinstance(token, str) or not token:
        raise ValueError('Xray authentication returned no token')
    query = {'projectKey': os.environ.get('XRAY_PROJECT_KEY') or 'PT'}
    for variable, parameter in [('XRAY_TEST_PLAN_KEY', 'testPlanKey'), ('XRAY_TEST_EXEC_KEY', 'testExecKey')]:
        if os.environ.get(variable):
            query[parameter] = os.environ[variable]
    result = post('/import/execution/junit?' + urllib.parse.urlencode(query), report.read_bytes(), 'text/xml', token, timeout=600)
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
    except (ValueError, OSError) as error:
        raise SystemExit(str(error)) from None
    print(f'Xray Test Execution: {execution}')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write(f'Xray Test Execution: **{execution}**\n')
