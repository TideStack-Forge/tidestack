#!/usr/bin/env python3
"""LF-DBG-PERF-001: three independent warmed JVM forks per mode; retain every sample."""
import argparse
import json
from pathlib import Path
import statistics
import subprocess
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--classpath-report', type=Path, required=True)
    parser.add_argument('--java', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    repo = args.repo.resolve()
    output = args.output.resolve()
    output.relative_to(repo / 'tmp')
    output.mkdir(parents=True, exist_ok=False)
    properties = {node.attrib['name']: node.attrib.get('value', '')
                  for node in ET.parse(args.classpath_report).findall('./properties/property')}
    classpath = properties.get('surefire.test.class.path', properties.get('java.class.path'))
    if not classpath:
        raise SystemExit('Missing actual test classpath in Surefire report')
    baseline = ':'.join(path for path in classpath.split(':') if 'ouroboros-logicflow-debug-helper' not in path)
    results = {mode: [] for mode in ('baseline', 'idle', 'active')}
    for fork in range(1, 4):
        modes = ('baseline', 'idle', 'active') if fork % 2 else ('active', 'idle', 'baseline')
        for mode in modes:
            result = output / f'{fork}-{mode}.json'
            command = [args.java, '-Xms256m', '-Xmx256m', '-cp', baseline if mode == 'baseline' else classpath,
                       'com.ouroboros.develop.logicflowdebug.DebugPerformanceFixture', mode, str(result)]
            with (output / f'{fork}-{mode}.log').open('w') as log:
                subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT, timeout=120, check=True)
            results[mode].append(json.loads(result.read_text()))
    medians = {mode: statistics.median(sample['p95Nanos'] for sample in samples)
               for mode, samples in results.items()}
    idle_increase = medians['idle'] / medians['baseline'] - 1
    active_increase = medians['active'] - medians['baseline']
    passed = idle_increase <= .02 and active_increase <= max(medians['baseline'] * .10, 5_000_000)
    summary = {'case': 'LF-DBG-PERF-001', 'forksPerMode': 3, 'p95MedianNanos': medians,
               'idleIncreaseRatio': idle_increase, 'activeIncreaseNanos': active_increase,
               'passed': passed, 'samples': results}
    (output / 'summary.json').write_text(json.dumps(summary, indent=2))
    print(json.dumps({key: value for key, value in summary.items() if key != 'samples'}, indent=2))
    if not passed:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
