import {runTests} from '@vscode/test-electron'
import {mkdtemp, mkdir, rm} from 'node:fs/promises'
import {dirname, join, resolve} from 'node:path'
import {tmpdir} from 'node:os'
import {fileURLToPath} from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const repo = resolve(root, '../..')
const tempRoot = join(repo, 'tmp/ide-metadata-fixture')
await mkdir(tempRoot, {recursive: true})
// macOS Unix socket paths cannot fit a long worktree prefix.
const isolated = await mkdtemp(join(process.platform === 'darwin' ? '/tmp' : tmpdir(), 'ob-ide-'))
const workspace = join(isolated, 'workspace')
await mkdir(workspace)
try {
  await runTests({
    extensionDevelopmentPath: root,
    cachePath: join(tempRoot, 'vscode-cache'),
    extensionTestsPath: join(root, 'tests/native/index.cjs'),
    extensionTestsEnv: {OB_IDE_TEST_WORKSPACE: workspace},
    ...(process.env.OB_VSCODE_EXECUTABLE ? {vscodeExecutablePath: process.env.OB_VSCODE_EXECUTABLE} : {version: '1.95.3'}),
    launchArgs: [workspace, '--user-data-dir=' + join(isolated, 'user'), '--extensions-dir=' + join(isolated, 'extensions'),
      '--disable-extensions', '--skip-welcome', '--skip-release-notes', '--disable-workspace-trust'],
  })
} finally { await rm(isolated, {recursive: true, force: true}) }
