import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const packageRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const repoRoot = join(packageRoot, '..', '..', '..')

const editors = [
  {
    name: 'LogicFlow editor',
    root: join(repoRoot, 'frontends', 'web', 'ouroboros-logicflow-editor'),
    requiredFiles: [
      'dist/static/js/app.js',
      'dist/static/css/app.css',
    ],
  },
  {
    name: 'Approval workflow editor',
    root: join(repoRoot, 'frontends', 'web', 'ouroboros-approval-workflow-designer'),
    requiredFiles: [
      'dist/static/js/app.js',
      'dist/static/css/app.css',
    ],
  },
]

for (const editor of editors) {
  execFileSync('npm', ['run', 'build', '--prefix', editor.root], {
    stdio: 'inherit',
    env: {
      ...process.env,
      OUROBOROS_EDITOR_PUBLIC_PATH: 'auto',
    },
  })
  const missing = editor.requiredFiles.filter((relativePath) => !existsSync(join(editor.root, relativePath)))
  if (missing.length) {
    throw new Error(`${editor.name} build did not produce required IDE bundle assets:\n${missing.map((item) => `- ${join(editor.root, item)}`).join('\n')}`)
  }
}
