import io
import urllib.error
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
from scripts.xray_report import load_mappings, merge_reports
from scripts.xray_upload import post, upload


class XrayReportsTest(unittest.TestCase):
    def test_merge_preserves_outcomes_and_links_requirements(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'TEST-HubAcceptanceIT.xml'
            path.write_text('''<testsuite name="Hubs"><properties><property name="secret" value="private"/></properties>
                <testcase classname="Scenario" name="created"/>
                <testcase classname="Scenario" name="rejected"><failure message="expected"/></testcase>
                <testcase classname="Scenario" name="PT-34 skipped"><skipped/><system-out>private</system-out></testcase>
                <testcase classname="Scenario" name="broken"><error message="database"/></testcase>
                <system-out>private</system-out></testsuite>''')
            root = merge_reports([path], {'*HubAcceptanceIT*': ['PT-33']})
            self.assertEqual((root.get('tests'), root.get('failures'), root.get('errors'), root.get('skipped')), ('4', '1', '1', '1'))
            cases = root.findall('./testsuite/testcase')
            self.assertEqual(cases[0].find('properties/property').get('value'), 'PT-33')
            self.assertEqual(cases[2].find('properties/property').get('value'), 'PT-33,PT-34')
            self.assertNotIn('private', ET.tostring(root, encoding='unicode'))
            self.assertEqual(cases[1].find('failure').get('message'), 'expected')

    def test_existing_test_key_and_requirements_survive(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'frontend.xml'
            path.write_text('''<testsuites><testsuite name="frontend"><testcase name="PT-33 adds hub">
                <properties><property name="test_key" value="PT-900"/>
                <property name="requirements" value="PT-10"/></properties>
                </testcase></testsuite></testsuites>''')
            case = merge_reports([path], {}).find('./testsuite/testcase')
            props = {prop.get('name'): prop.get('value') for prop in case.find('properties')}
            self.assertEqual(props, {'test_key': 'PT-900', 'requirements': 'PT-10,PT-33'})

    def test_merges_multiple_producers_with_optional_story_mappings(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            mapping = folder / 'xray-requirements.json'
            mapping.write_text('{"*Fleet*": ["PT-32"]}')
            (folder / 'xray-hub-requirements.json').write_text('{"*Hub*": ["PT-33"]}')
            backend = folder / 'backend.xml'
            backend.write_text('<testsuite name="Fleet"><testcase name="add"/></testsuite>')
            browser = folder / 'browser.xml'
            browser.write_text('<testsuite name="HubPageIT"><testcase name="edit"><failure/></testcase></testsuite>')
            root = merge_reports([backend, browser], load_mappings(mapping))
            self.assertEqual(root.get('tests'), '2')
            self.assertEqual(root.get('failures'), '1')
            self.assertEqual([p.get('value') for p in root.findall('.//property')], ['PT-32', 'PT-33'])

    def test_empty_reports_fail(self):
        with self.assertRaisesRegex(ValueError, 'empty execution'):
            merge_reports([], {})

    @patch.dict(os.environ, {'XRAY_CLIENT_SECRET': 'synthetic-secret'}, clear=True)
    @patch('scripts.xray_upload.urllib.request.urlopen')
    def test_import_error_reports_body_without_credentials(self, urlopen):
        urlopen.side_effect = urllib.error.HTTPError(
            'https://xray.cloud.getxray.app/api/v2/import/execution/junit?projectKey=PT',
            400, 'Bad Request', {}, io.BytesIO(
                b'{"error":"Missing Test Execution issue type", "debug":"synthetic-token synthetic-secret"}'))
        with self.assertRaises(ValueError) as caught:
            post('/import/execution/junit?projectKey=PT', b'<testsuites/>', 'text/xml', 'synthetic-token')
        message = str(caught.exception)
        self.assertIn('HTTP 400', message)
        self.assertIn('Missing Test Execution issue type', message)
        self.assertNotIn('synthetic-token', message)
        self.assertNotIn('synthetic-secret', message)

    @patch.dict(os.environ, {}, clear=True)
    @patch('scripts.xray_upload.post')
    def test_missing_credentials_never_contacts_xray(self, post):
        with self.assertRaisesRegex(ValueError, 'XRAY_CLIENT_ID'):
            upload(Path('unused'))
        post.assert_not_called()

    @patch.dict(os.environ, {'XRAY_CLIENT_ID': ' synthetic-id\n', 'XRAY_CLIENT_SECRET': 'synthetic-secret\n',
                             'XRAY_PROJECT_KEY': 'PT', 'XRAY_TEST_PLAN_KEY': 'PT-500'}, clear=True)
    @patch('scripts.xray_upload.post', side_effect=['synthetic-token', {'testExecIssue': {'key': 'PT-501'}}])
    def test_upload_authenticates_and_sends_report_with_plan(self, post):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'results.xml'
            report.write_text('<testsuites/>')
            self.assertEqual(upload(report), 'PT-501')
        auth, request = post.call_args_list
        self.assertEqual(request.kwargs, {'timeout': 600})
        self.assertEqual(auth.args[0], '/authenticate')
        self.assertEqual(json.loads(auth.args[1]), {'client_id': 'synthetic-id', 'client_secret': 'synthetic-secret'})
        self.assertEqual(request.args, ('/import/execution/junit?projectKey=PT&testPlanKey=PT-500', b'<testsuites/>', 'text/xml', 'synthetic-token'))


if __name__ == '__main__':
    unittest.main()
