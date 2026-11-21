import { mkdtemp, mkdir, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { expect, test } from 'vitest';

import { auditFrontendAgentEntrypoints } from '../../../scripts/validate-agent-entrypoints.mjs';

test('current frontend doc chain has no entrypoint findings', async () => {
  const repoRoot = path.resolve(process.cwd(), '..', '..');
  const findings = await auditFrontendAgentEntrypoints({
    repoRoot,
    frontendRoot: path.join(repoRoot, 'frontends/web'),
  });

  expect(findings).toEqual([]);
});

test('audit reports missing files and broken markdown links', async () => {
  const sandboxRoot = await mkdtemp(path.join(os.tmpdir(), 'frontend-entry-audit-'));
  const repoRoot = path.join(sandboxRoot, 'repo');
  const frontendRoot = path.join(repoRoot, 'frontends/web');
  const packageRoot = path.join(frontendRoot, 'demo-app');

  await mkdir(path.join(packageRoot, 'tests'), { recursive: true });

  await writeFile(path.join(repoRoot, 'AGENTS.md'), '[Web](frontends/web/AGENTS.md)\n');
  await writeFile(path.join(repoRoot, 'CLAUDE.md'), '[Web](frontends/web/AGENTS.md)\n');
  await writeFile(path.join(repoRoot, 'GEMINI.md'), '[Web](frontends/web/AGENTS.md)\n');

  await writeFile(path.join(frontendRoot, 'package.json'), '{ "name": "frontend-root" }\n');
  await writeFile(path.join(frontendRoot, 'AGENTS.md'), '[Testing](TESTING.md)\n');
  await writeFile(path.join(frontendRoot, 'CLAUDE.md'), '[Testing](TESTING.md)\n');
  await writeFile(path.join(frontendRoot, 'GEMINI.md'), '[Testing](TESTING.md)\n');
  await writeFile(path.join(frontendRoot, 'README.md'), '# web\n');
  await writeFile(path.join(frontendRoot, 'TESTING.md'), '# testing\n');

  await writeFile(path.join(packageRoot, 'package.json'), '{ "name": "demo-app" }\n');
  await writeFile(path.join(packageRoot, 'AGENTS.md'), '[Tests](tests/README.md)\n');
  await writeFile(path.join(packageRoot, 'CLAUDE.md'), '[Missing](missing.md)\n');
  await writeFile(path.join(packageRoot, 'GEMINI.md'), '# gemini\n');

  const findings = await auditFrontendAgentEntrypoints({ repoRoot, frontendRoot });

  expect(findings).toEqual([
    'Missing required file: frontends/web/demo-app/tests/README.md',
    'Broken markdown link in frontends/web/demo-app/CLAUDE.md: missing.md',
  ]);
});
