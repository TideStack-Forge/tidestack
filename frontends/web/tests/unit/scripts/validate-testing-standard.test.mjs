import { mkdtemp, mkdir, readFile, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { expect, test } from 'vitest';

import { auditFrontendTestingStandard } from '../../../scripts/audit-testing-standard.mjs';

test('current frontend testing standard has no package findings', async () => {
  const repoRoot = path.resolve(process.cwd(), '..', '..');
  const findings = await auditFrontendTestingStandard({
    frontendRoot: path.join(repoRoot, 'frontends/web'),
  });

  expect(findings).toEqual([]);
});

test('frontend root test:unit is backed by Vitest', async () => {
  const packageJsonPath = path.resolve(process.cwd(), 'package.json');
  const packageJson = JSON.parse(await readFile(packageJsonPath, 'utf8'));

  expect(String(packageJson.scripts?.['test:unit'] ?? '')).toMatch(/vitest/);
});

test('audit reports missing testing docs, runners, and deprecated aliases', async () => {
  const sandboxRoot = await mkdtemp(path.join(os.tmpdir(), 'frontend-testing-audit-'));
  const frontendRoot = path.join(sandboxRoot, 'frontends/web');
  const packageRoot = path.join(frontendRoot, 'demo-app');

  await mkdir(packageRoot, { recursive: true });
  await writeFile(
    path.join(packageRoot, 'package.json'),
    JSON.stringify({
      name: 'demo-app',
      scripts: {
        test: 'npm run test:unit',
        'test:unit': 'vitest run',
        'test:node': 'npm run test:unit --',
      },
    }, null, 2),
  );
  await writeFile(path.join(packageRoot, 'vitest.config.js'), 'export default {};\n');

  const findings = await auditFrontendTestingStandard({ frontendRoot });

  expect(findings).toEqual([
    'demo-app: missing tests/ directory',
    'demo-app: missing tests/README.md',
    'demo-app: deprecated test alias test:node',
  ]);
});

test('audit accepts playwright-backed integration scripts', async () => {
  const sandboxRoot = await mkdtemp(path.join(os.tmpdir(), 'frontend-testing-audit-integration-'));
  const frontendRoot = path.join(sandboxRoot, 'frontends/web');
  const packageRoot = path.join(frontendRoot, 'demo-app');

  await mkdir(path.join(packageRoot, 'tests'), { recursive: true });
  await writeFile(
    path.join(packageRoot, 'package.json'),
    JSON.stringify({
      name: 'demo-app',
      scripts: {
        test: 'npm run test:all',
        'test:unit': 'vitest run',
        'test:integration': 'playwright test',
        'test:all': 'npm run test:unit && npm run test:integration',
      },
    }, null, 2),
  );
  await writeFile(path.join(packageRoot, 'tests/README.md'), '# tests\n');
  await writeFile(path.join(packageRoot, 'vitest.config.js'), 'export default {};\n');
  await writeFile(path.join(packageRoot, 'playwright.config.js'), 'module.exports = {};\n');

  const findings = await auditFrontendTestingStandard({ frontendRoot });

  expect(findings).toEqual([]);
});
