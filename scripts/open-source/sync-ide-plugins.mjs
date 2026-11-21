#!/usr/bin/env node
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import {
  chmodSync,
  copyFileSync,
  cpSync,
  existsSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_PATH = fileURLToPath(import.meta.url)
const SCRIPT_DIR = dirname(SCRIPT_PATH)
const REPO_ROOT = gitOutput(['rev-parse', '--show-toplevel'], resolve(SCRIPT_DIR, '../..'))
const DEFAULT_MANIFEST = 'ide-plugins/sync.manifest.json'
const LEDGER_FILE = '.tidestack-sync.json'
let activeRepositoriesByName = new Map()

function usage() {
  console.log(`Usage: sync-ide-plugins.mjs [--check|--sync] [--repo NAME|--all] [--verify] [--commit] [--push]

Synchronizes TideStack Develop IDE plugin standalone repositories from the monorepo.
The default mode is --check. --sync writes target repositories. --commit and --push
only operate on selected target repositories after a successful sync.

Options:
  --manifest PATH        Manifest path relative to the monorepo root. Default: ${DEFAULT_MANIFEST}
  --repo NAME            Select one repository from the manifest. Can be repeated.
  --all                  Select every repository from the manifest.
  --check                Compare target repositories without writing anything. Default.
  --sync                 Update selected target repositories.
  --verify               Run each selected repository's verify commands.
  --commit               Commit changed managed paths in target repositories.
  --push                 Push selected target repositories to origin/current-branch.
  --allow-dirty-target   Allow --sync to run against a dirty target repository.
  -h, --help             Show this help.
`)
}

function main() {
  const options = parseArgs(process.argv.slice(2))
  if (options.help) {
    usage()
    return
  }

  const manifest = readManifest(resolve(REPO_ROOT, options.manifest))
  activeRepositoriesByName = new Map(manifest.repositories.map((repository) => [repository.name, repository]))
  const repositories = selectRepositories(manifest.repositories, options)
  const revision = spawnSync('git', ['rev-parse', '--verify', 'HEAD'], { cwd: REPO_ROOT, encoding: 'utf8' })
  if (revision.status !== 0 && options.mode !== 'check') {
    fail('A source commit is required before synchronizing repositories', 2)
  }
  const sourceCommit = revision.status === 0 ? revision.stdout.trim() : 'unborn-working-tree'
  const exportedCommits = new Map()
  let failed = false

  for (const repository of repositories) {
    const result = synchronizeRepository(repository, manifest, options, sourceCommit, exportedCommits)
    if (!result.ok) {
      failed = true
    }
    if (result.targetCommit) {
      exportedCommits.set(repository.name, result.targetCommit)
    }
  }

  if (failed) {
    process.exit(1)
  }
}

function parseArgs(args) {
  const options = {
    manifest: DEFAULT_MANIFEST,
    mode: 'check',
    repos: [],
    all: false,
    verify: false,
    commit: false,
    push: false,
    allowDirtyTarget: false,
    help: false,
  }
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index]
    switch (arg) {
      case '--manifest':
        options.manifest = requireValue(args, ++index, arg)
        break
      case '--repo':
        options.repos.push(requireValue(args, ++index, arg))
        break
      case '--all':
        options.all = true
        break
      case '--check':
        options.mode = 'check'
        break
      case '--sync':
        options.mode = 'sync'
        break
      case '--verify':
        options.verify = true
        break
      case '--commit':
        options.commit = true
        break
      case '--push':
        options.push = true
        break
      case '--allow-dirty-target':
        options.allowDirtyTarget = true
        break
      case '-h':
      case '--help':
        options.help = true
        break
      default:
        fail(`Unknown argument: ${arg}`, 2)
    }
  }
  if (options.mode === 'check' && (options.commit || options.push)) {
    fail('--commit and --push require --sync', 2)
  }
  return options
}

function requireValue(args, index, flag) {
  const value = args[index]
  if (!value || value.startsWith('--')) {
    fail(`Missing value for ${flag}`, 2)
  }
  return value
}

function readManifest(path) {
  if (!existsSync(path)) {
    fail(`Missing sync manifest: ${path}`, 1)
  }
  const manifest = JSON.parse(readFileSync(path, 'utf8'))
  if (manifest.version !== 1) {
    fail(`Unsupported sync manifest version: ${manifest.version}`, 1)
  }
  if (!Array.isArray(manifest.repositories)) {
    fail('Sync manifest must define repositories[]', 1)
  }
  return manifest
}

function selectRepositories(repositories, options) {
  if (options.all || options.repos.length === 0) {
    return repositories
  }
  const byName = new Map(repositories.map((repository) => [repository.name, repository]))
  return options.repos.map((name) => {
    const repository = byName.get(name)
    if (!repository) {
      fail(`Unknown repository in manifest: ${name}`, 2)
    }
    return repository
  })
}

function synchronizeRepository(repository, manifest, options, sourceCommit, exportedCommits) {
  const targetRoot = resolve(REPO_ROOT, repository.target)
  const tempRoot = mkdtempSync(join(tmpdir(), `tidestack-${repository.name}-sync-`))
  try {
    assertTargetRepository(repository, targetRoot)
    if (options.mode === 'sync' && !options.allowDirtyTarget) {
      requireCleanTarget(repository, targetRoot)
    }

    const snapshot = buildSnapshot(repository, targetRoot, tempRoot, sourceCommit, exportedCommits)
    const drift = compareSnapshot(snapshot.root, targetRoot, snapshot.managedPaths, repository.ignoredInManagedPaths || [])
    const submoduleDrift = compareSubmodules(repository, targetRoot, exportedCommits)
    const allDrift = drift.concat(submoduleDrift)

    if (options.mode === 'check') {
      if (allDrift.length > 0) {
        reportDrift(repository, allDrift)
        return { ok: false }
      }
      console.log(`OK ${repository.name}: target matches monorepo export`)
      return { ok: true, targetCommit: gitOutput(['rev-parse', 'HEAD'], targetRoot) }
    }

    applySnapshot(snapshot.root, targetRoot, snapshot.managedPaths, repository.ignoredInManagedPaths || [])
    updateSubmodules(repository, targetRoot, exportedCommits)

    if (options.verify) {
      runVerify(repository, targetRoot)
      applySnapshot(snapshot.root, targetRoot, snapshot.managedPaths, repository.ignoredInManagedPaths || [])
      updateSubmodules(repository, targetRoot, exportedCommits)
    }
    if (options.commit) {
      commitManagedChanges(repository, targetRoot, snapshot.managedPaths)
    }
    if (options.push) {
      pushTarget(repository, targetRoot)
    }

    const targetCommit = gitOutput(['rev-parse', 'HEAD'], targetRoot)
    console.log(`OK ${repository.name}: synchronized to ${targetRoot}`)
    return { ok: true, targetCommit }
  } catch (error) {
    console.error(`FAIL ${repository.name}: ${error.message}`)
    return { ok: false }
  } finally {
    rmSync(tempRoot, { recursive: true, force: true })
  }
}

function assertTargetRepository(repository, targetRoot) {
  if (!existsSync(targetRoot)) {
    throw new Error(`Target repository does not exist: ${targetRoot}`)
  }
  const root = gitOutput(['rev-parse', '--show-toplevel'], targetRoot)
  if (root !== targetRoot) {
    throw new Error(`Target is not a repository root: ${targetRoot}`)
  }
  if (!Array.isArray(repository.managedPaths) || repository.managedPaths.length === 0) {
    throw new Error('Repository has no managedPaths')
  }
}

function requireCleanTarget(repository, targetRoot) {
  const status = gitOutput(['status', '--porcelain', '--untracked-files=all'], targetRoot)
  if (status.trim()) {
    throw new Error(`Target repository is dirty; commit/stash first or use --allow-dirty-target:\n${status}`)
  }
  for (const submodule of repository.submodules || []) {
    const submoduleRoot = join(targetRoot, submodule.path)
    if (existsSync(submoduleRoot)) {
      const submoduleStatus = gitOutput(['status', '--porcelain', '--untracked-files=all'], submoduleRoot)
      if (submoduleStatus.trim()) {
        throw new Error(`Target submodule is dirty: ${submodule.path}\n${submoduleStatus}`)
      }
    }
  }
}

function buildSnapshot(repository, targetRoot, tempRoot, sourceCommit, exportedCommits) {
  const snapshotRoot = join(tempRoot, repository.name)
  mkdirSync(snapshotRoot, { recursive: true })
  const managedPaths = []

  for (const entry of repository.managedPaths) {
    const sourceRoot = entry.fromRepoRoot ? REPO_ROOT : resolve(REPO_ROOT, repository.sourceRoot)
    const sourcePath = resolve(sourceRoot, entry.source)
    if (!existsSync(sourcePath)) {
      throw new Error(`Missing managed source path: ${sourcePath}`)
    }
    const targetPath = normalizePath(entry.target || entry.source)
    copyManagedPath(
      sourcePath,
      join(snapshotRoot, targetPath),
      (entry.exclude || []).concat(repository.ignoredInManagedPaths || []),
    )
    managedPaths.push(targetPath)
  }

  for (const transform of repository.transforms || []) {
    applyTransform(transform, snapshotRoot, targetRoot)
    managedPaths.push(normalizePath(transform.path))
  }

  for (const retiredPath of repository.retiredPaths || []) {
    managedPaths.push(normalizePath(retiredPath))
  }

  writeLedger(snapshotRoot, repository, sourceCommit, exportedCommits, managedPaths)
  managedPaths.push(LEDGER_FILE)

  return {
    root: snapshotRoot,
    managedPaths: compactManagedPaths(managedPaths),
  }
}

function copyManagedPath(sourcePath, targetPath, excludes) {
  mkdirSync(dirname(targetPath), { recursive: true })
  cpSync(sourcePath, targetPath, {
    recursive: true,
    force: true,
    filter: (source) => {
      const relativePath = normalizePath(relative(sourcePath, source))
      return relativePath === '' || !isIgnored(relativePath, excludes)
    },
  })
}

function applyTransform(transform, snapshotRoot, targetRoot) {
  switch (transform.type) {
    case 'jsonSet':
      applyJsonSet(transform, snapshotRoot)
      return
    case 'textReplace':
      applyTextReplace(transform, snapshotRoot)
      return
    case 'preserveXmlCdataElement':
      applyPreserveXmlCdataElement(transform, snapshotRoot, targetRoot)
      return
    default:
      throw new Error(`Unsupported transform type: ${transform.type}`)
  }
}

function applyJsonSet(transform, snapshotRoot) {
  const path = join(snapshotRoot, transform.path)
  const document = JSON.parse(readFileSync(path, 'utf8'))
  for (const [keyPath, value] of Object.entries(transform.values || {})) {
    setJsonValue(document, keyPath.split('.'), value)
  }
  writeFileSync(path, `${JSON.stringify(document, null, 2)}\n`, 'utf8')
}

function setJsonValue(document, pathParts, value) {
  let current = document
  for (const part of pathParts.slice(0, -1)) {
    if (!Object.prototype.hasOwnProperty.call(current, part) || typeof current[part] !== 'object' || current[part] === null) {
      current[part] = {}
    }
    current = current[part]
  }
  current[pathParts[pathParts.length - 1]] = value
}

function applyTextReplace(transform, snapshotRoot) {
  const path = join(snapshotRoot, transform.path)
  let text = readFileSync(path, 'utf8')
  for (const replacement of transform.replacements || []) {
    if (!text.includes(replacement.from)) {
      throw new Error(`Replacement source not found in ${transform.path}: ${replacement.from}`)
    }
    text = text.split(replacement.from).join(replacement.to)
  }
  writeFileSync(path, text, 'utf8')
}

function applyPreserveXmlCdataElement(transform, snapshotRoot, targetRoot) {
  const snapshotPath = join(snapshotRoot, transform.path)
  const targetPath = join(targetRoot, transform.path)
  if (!existsSync(targetPath)) {
    return
  }
  const current = readFileSync(targetPath, 'utf8')
  const expected = readFileSync(snapshotPath, 'utf8')
  const currentElement = extractXmlCdataElement(current, transform.element)
  if (!currentElement) {
    return
  }
  const expectedElement = extractXmlCdataElement(expected, transform.element)
  if (!expectedElement) {
    throw new Error(`Cannot preserve missing <${transform.element}> in ${transform.path}`)
  }
  writeFileSync(snapshotPath, expected.replace(expectedElement.fullText, currentElement.fullText), 'utf8')
}

function extractXmlCdataElement(text, element) {
  const pattern = new RegExp(`<${escapeRegExp(element)}>\\s*<!\\[CDATA\\[[\\s\\S]*?]]>\\s*</${escapeRegExp(element)}>`, 'm')
  const match = text.match(pattern)
  return match ? { fullText: match[0] } : null
}

function writeLedger(snapshotRoot, repository, sourceCommit, exportedCommits, managedPaths) {
  const managedContentHash = hashManagedContent(snapshotRoot, compactManagedPaths(managedPaths))
  const submodules = Object.fromEntries((repository.submodules || []).map((submodule) => [
    submodule.path,
    exportedCommits.get(submodule.repository) || resolveTargetRepositoryCommit(submodule.repository),
  ]))
  const ledger = {
    schemaVersion: 1,
    managedBy: 'scripts/open-source/sync-ide-plugins.mjs',
    sourceRepository: 'ouroboros',
    sourceCommit,
    sourceManagedContentHash: managedContentHash,
    targetRepository: repository.name,
    managedPaths: compactManagedPaths(managedPaths),
  }
  if (Object.keys(submodules).length > 0) {
    ledger.submodules = submodules
  }
  writeFileSync(join(snapshotRoot, LEDGER_FILE), `${JSON.stringify(ledger, null, 2)}\n`, 'utf8')
}

function compareSnapshot(snapshotRoot, targetRoot, managedPaths, ignoredPatterns) {
  const drift = []
  for (const managedPath of managedPaths) {
    comparePath(join(snapshotRoot, managedPath), join(targetRoot, managedPath), managedPath, ignoredPatterns, drift)
  }
  return drift
}

function comparePath(snapshotPath, targetPath, displayPath, ignoredPatterns, drift) {
  if (isIgnored(displayPath, ignoredPatterns)) {
    return
  }
  const snapshotExists = existsSync(snapshotPath)
  const targetExists = existsSync(targetPath)
  if (!snapshotExists && !targetExists) {
    return
  }
  if (!snapshotExists) {
    drift.push({ path: displayPath, reason: 'extra in target' })
    return
  }
  if (!targetExists) {
    drift.push({ path: displayPath, reason: 'missing in target' })
    return
  }
  const snapshotStat = lstatSync(snapshotPath)
  const targetStat = lstatSync(targetPath)
  if (snapshotStat.isDirectory() !== targetStat.isDirectory()) {
    drift.push({ path: displayPath, reason: 'type differs' })
    return
  }
  if (snapshotStat.isDirectory()) {
    const childNames = new Set(readdirSync(snapshotPath).concat(readdirSync(targetPath)))
    for (const childName of [...childNames].sort()) {
      comparePath(join(snapshotPath, childName), join(targetPath, childName), normalizePath(join(displayPath, childName)), ignoredPatterns, drift)
    }
    return
  }
  if (snapshotStat.isSymbolicLink() || targetStat.isSymbolicLink()) {
    drift.push({ path: displayPath, reason: 'symlink comparison is unsupported' })
    return
  }
  if (hashFile(snapshotPath) !== hashFile(targetPath)) {
    drift.push({ path: displayPath, reason: 'content differs' })
    return
  }
  if (executableBit(snapshotStat) !== executableBit(targetStat)) {
    drift.push({ path: displayPath, reason: 'executable bit differs' })
  }
}

function applySnapshot(snapshotRoot, targetRoot, managedPaths, ignoredPatterns) {
  for (const managedPath of managedPaths) {
    const snapshotPath = join(snapshotRoot, managedPath)
    const targetPath = join(targetRoot, managedPath)
    if (!existsSync(snapshotPath)) {
      rmSync(targetPath, { recursive: true, force: true })
      continue
    }
    syncPath(snapshotPath, targetPath, managedPath, ignoredPatterns)
  }
}

function syncPath(snapshotPath, targetPath, displayPath, ignoredPatterns) {
  const snapshotStat = lstatSync(snapshotPath)
  if (!snapshotStat.isDirectory()) {
    mkdirSync(dirname(targetPath), { recursive: true })
    copyFileSync(snapshotPath, targetPath)
    chmodSync(targetPath, snapshotStat.mode)
    return
  }
  mkdirSync(targetPath, { recursive: true })
  removeExtraManagedChildren(snapshotPath, targetPath, displayPath, ignoredPatterns)
  cpSync(snapshotPath, targetPath, { recursive: true, force: true })
}

function removeExtraManagedChildren(snapshotPath, targetPath, displayPath, ignoredPatterns) {
  if (!existsSync(targetPath)) {
    return
  }
  for (const childName of readdirSync(targetPath)) {
    const childDisplayPath = normalizePath(join(displayPath, childName))
    if (isIgnored(childDisplayPath, ignoredPatterns)) {
      continue
    }
    const snapshotChildPath = join(snapshotPath, childName)
    const targetChildPath = join(targetPath, childName)
    if (!existsSync(snapshotChildPath)) {
      rmSync(targetChildPath, { recursive: true, force: true })
      continue
    }
    if (lstatSync(snapshotChildPath).isDirectory() && lstatSync(targetChildPath).isDirectory()) {
      removeExtraManagedChildren(snapshotChildPath, targetChildPath, childDisplayPath, ignoredPatterns)
    }
  }
}

function compareSubmodules(repository, targetRoot, exportedCommits) {
  const drift = []
  for (const submodule of repository.submodules || []) {
    const expectedCommit = exportedCommits.get(submodule.repository) || resolveTargetRepositoryCommit(submodule.repository)
    const submoduleRoot = join(targetRoot, submodule.path)
    if (!existsSync(submoduleRoot)) {
      drift.push({ path: submodule.path, reason: `missing submodule; expected ${expectedCommit}` })
      continue
    }
    const actualCommit = gitOutput(['rev-parse', 'HEAD'], submoduleRoot)
    if (actualCommit !== expectedCommit) {
      drift.push({ path: submodule.path, reason: `submodule at ${actualCommit}, expected ${expectedCommit}` })
    }
  }
  return drift
}

function updateSubmodules(repository, targetRoot, exportedCommits) {
  for (const submodule of repository.submodules || []) {
    const expectedCommit = exportedCommits.get(submodule.repository) || resolveTargetRepositoryCommit(submodule.repository)
    const submoduleRoot = join(targetRoot, submodule.path)
    if (!existsSync(submoduleRoot)) {
      throw new Error(`Missing submodule path: ${submodule.path}`)
    }
    runGit(['fetch', 'origin'], submoduleRoot)
    runGit(['checkout', expectedCommit], submoduleRoot)
  }
}

function resolveTargetRepositoryCommit(repositoryName) {
  const repository = activeRepositoriesByName.get(repositoryName)
  if (!repository) {
    throw new Error(`Unknown repository referenced by submodule: ${repositoryName}`)
  }
  return gitOutput(['rev-parse', 'HEAD'], resolve(REPO_ROOT, repository.target))
}

function runVerify(repository, targetRoot) {
  for (const command of repository.verify || []) {
    console.log(`VERIFY ${repository.name}: ${command}`)
    const result = spawnSync(command, { cwd: targetRoot, shell: true, stdio: 'inherit' })
    if (result.status !== 0) {
      throw new Error(`Verify command failed: ${command}`)
    }
  }
}

function commitManagedChanges(repository, targetRoot, managedPaths) {
  const addPaths = compactManagedPaths(managedPaths.concat((repository.submodules || []).map((submodule) => submodule.path)))
    .filter((managedPath) => existsSync(join(targetRoot, managedPath)) || gitOutput(['ls-files', '--', managedPath], targetRoot))
  if (addPaths.length > 0) {
    runGit(['add', '-A', '--', ...addPaths], targetRoot)
  }
  const status = gitOutput(['status', '--porcelain', '--untracked-files=all'], targetRoot)
  if (!status.trim()) {
    console.log(`SKIP ${repository.name}: no changes to commit`)
    return
  }
  runGit(['commit', '-m', repository.commitMessage || `Sync ${repository.displayName || repository.name} from monorepo`], targetRoot)
}

function pushTarget(repository, targetRoot) {
  const branch = gitOutput(['branch', '--show-current'], targetRoot)
  if (!branch) {
    throw new Error('Cannot push detached HEAD')
  }
  runGit(['push', 'origin', branch], targetRoot)
  console.log(`PUSH ${repository.name}: origin/${branch}`)
}

function reportDrift(repository, drift) {
  console.error(`DRIFT ${repository.name}: ${drift.length} managed path(s) differ`)
  for (const item of drift.slice(0, 40)) {
    console.error(`- ${item.path}: ${item.reason}`)
  }
  if (drift.length > 40) {
    console.error(`... ${drift.length - 40} more`)
  }
}

function hashManagedContent(root, managedPaths) {
  const hash = createHash('sha256')
  for (const managedPath of managedPaths) {
    const absolutePath = join(root, managedPath)
    if (!existsSync(absolutePath)) {
      continue
    }
    for (const filePath of walkFiles(absolutePath)) {
      const rel = normalizePath(relative(root, filePath))
      hash.update(rel)
      hash.update('\0')
      hash.update(String(executableBit(statSync(filePath))))
      hash.update('\0')
      hash.update(readFileSync(filePath))
      hash.update('\0')
    }
  }
  return hash.digest('hex')
}

function walkFiles(root) {
  const stat = lstatSync(root)
  if (!stat.isDirectory()) {
    return [root]
  }
  return readdirSync(root)
    .flatMap((childName) => walkFiles(join(root, childName)))
    .sort()
}

function hashFile(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex')
}

function executableBit(stat) {
  return stat.mode & 0o111 ? 1 : 0
}

function compactManagedPaths(paths) {
  const normalized = [...new Set(paths.map(normalizePath))].sort()
  const compact = []
  for (const path of normalized) {
    if (!compact.some((parent) => path !== parent && path.startsWith(`${parent}/`))) {
      compact.push(path)
    }
  }
  return compact
}

function isIgnored(path, patterns) {
  return patterns.some((pattern) => matchesPattern(path, normalizePath(pattern)))
}

function matchesPattern(path, pattern) {
  if (pattern.endsWith('/**') && !pattern.slice(0, -3).includes('*')) {
    const prefix = pattern.slice(0, -3)
    return path === prefix || path.startsWith(`${prefix}/`)
  }
  if (pattern.startsWith('**/') && pattern.endsWith('/**')) {
    const directory = pattern.slice(3, -3)
    return path === directory || path.endsWith(`/${directory}`) || path.includes(`/${directory}/`)
  }
  const regex = new RegExp(`^${pattern.split('**').map((part) => part.split('*').map(escapeRegExp).join('[^/]*')).join('.*')}$`)
  return regex.test(path)
}

function normalizePath(path) {
  return path.split(sep).join('/').replace(/^\.\//, '')
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function runGit(args, cwd) {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8', stdio: 'pipe' })
  if (result.status !== 0) {
    throw new Error(`git ${args.join(' ')} failed in ${cwd}:\n${result.stdout}${result.stderr}`)
  }
  if (result.stdout.trim()) {
    console.log(result.stdout.trim())
  }
}

function gitOutput(args, cwd) {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8', stdio: 'pipe' })
  if (result.status !== 0) {
    fail(`git ${args.join(' ')} failed in ${cwd}:\n${result.stdout}${result.stderr}`, 1)
  }
  return result.stdout.trim()
}

function fail(message, code = 1) {
  console.error(message)
  process.exit(code)
}

main()
