"""Merge JUnit reports and add Xray requirement links without changing outcomes."""
import argparse
import copy
import fnmatch
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def merge_reports(paths, mappings):
    merged = ET.Element('testsuites')
    for path in sorted(paths):
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == 'testsuite' else root.findall('.//testsuite')
        for original in suites:
            suite = copy.deepcopy(original)
            # JVM/environment properties and console logs do not belong in Xray.
            for child in list(suite):
                if child.tag in {'properties', 'system-out', 'system-err'}:
                    suite.remove(child)
            for case in suite.findall('testcase'):
                identities = [path.name, suite.get('name', ''), case.get('classname', '')]
                requirements = set(re.findall(r'\bPT-\d+\b', case.get('name', '')))
                for pattern, stories in mappings.items():
                    if any(fnmatch.fnmatchcase(value, pattern) for value in identities):
                        requirements.update(stories)
                for child in list(case):
                    if child.tag in {'system-out', 'system-err'}:
                        case.remove(child)
                if requirements:
                    props = case.find('properties')
                    if props is None:
                        props = ET.SubElement(case, 'properties')
                    for prop in list(props):
                        if prop.get('name') in {'requirement', 'requirements'}:
                            requirements.update(prop.get('value', '').split(','))
                            props.remove(prop)
                    ET.SubElement(props, 'property', name='requirements', value=','.join(sorted(requirements - {''})))
            suite.set('tests', str(len(suite.findall('testcase'))))
            if suite.findall('testcase'):
                merged.append(suite)
    cases = merged.findall('./testsuite/testcase')
    if not cases:
        raise ValueError('No JUnit test cases found; refusing to publish an empty execution')
    merged.set('tests', str(len(cases)))
    for outcome in ('failures', 'errors', 'skipped'):
        tag = {'failures': 'failure', 'errors': 'error', 'skipped': 'skipped'}[outcome]
        merged.set(outcome, str(sum(case.find(tag) is not None for case in cases)))
    return merged


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reports', type=Path, required=True)
    parser.add_argument('--mapping', type=Path, default=Path('scripts/xray-requirements.json'))
    parser.add_argument('--output', type=Path, default=Path('reports/xray.xml'))
    args = parser.parse_args()
    report = merge_reports(args.reports.rglob('*.xml'), json.loads(args.mapping.read_text()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(report).write(args.output, encoding='utf-8', xml_declaration=True)
    print(f'Prepared {report.get("tests")} test results in {args.output}')
