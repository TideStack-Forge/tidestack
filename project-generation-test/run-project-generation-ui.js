const fs = require('fs')
const path = require('path')
const childProcess = require('child_process')
const { createRequire } = require('module')
const { parseArgs } = require('node:util')
const { randomUUID } = require('node:crypto')

const { values } = parseArgs({
  options: {
    repo: { type: 'string' },
    output: { type: 'string' },
    check: { type: 'boolean' },
    help: { type: 'boolean' },
  },
})
if (values.help) {
  console.log('Usage: node run-project-generation-ui.js --repo /abs/checkout [--output /abs/new-evidence-dir] [--check]')
  process.exit(0)
}
if (!values.repo) {
  throw new Error('--repo /abs/checkout is required')
}
const REPO_ROOT = fs.realpathSync(values.repo)
const mainAppPackage = path.join(REPO_ROOT, 'frontends/web/main-app/package.json')
fs.accessSync(mainAppPackage)
const requireFromMainApp = createRequire(mainAppPackage)
const playwrightModule = requireFromMainApp.resolve('playwright')
const playwrightRelativePath = path.relative(REPO_ROOT, fs.realpathSync(playwrightModule))
if (playwrightRelativePath.startsWith(`..${path.sep}`) || path.isAbsolute(playwrightRelativePath)) {
  throw new Error(`playwright must be installed in the selected checkout: ${REPO_ROOT}`)
}

const APP_NAME = 'demo'
const BACKEND_BASE_URL = process.env.BACKEND_BASE_URL || 'http://127.0.0.1:18880'
const FRONTEND_BASE_URL = process.env.FRONTEND_BASE_URL || 'http://127.0.0.1:18081'
const OUTPUT_ROOT = path.resolve(values.output || path.join(
  REPO_ROOT, 'tmp/testing-evidence/project-generation', `${Date.now()}-${randomUUID()}`
))
const SCREENSHOT_DIR = path.join(OUTPUT_ROOT, 'screenshots')
const DOWNLOAD_DIR = path.join(OUTPUT_ROOT, 'downloads')
const LOG_DIR = path.join(OUTPUT_ROOT, 'logs')

const ROUTES = {
  micro: '/dev/demo/micro-web',
  backend: '/dev/demo/extend-jar-management',
  platformLibrary: '/platform-component-lib',
}

if (fs.existsSync(OUTPUT_ROOT)) {
  throw new Error(`Output directory already exists: ${OUTPUT_ROOT}; select a new evidence directory`)
}
if (values.check) {
  console.log(JSON.stringify({ repoRoot: REPO_ROOT, mainAppPackage, playwrightModule, outputRoot: OUTPUT_ROOT,
    backendBaseUrl: BACKEND_BASE_URL, frontendBaseUrl: FRONTEND_BASE_URL }, null, 2))
  process.exit(0)
}
const { chromium } = requireFromMainApp('playwright')
fs.mkdirSync(path.dirname(OUTPUT_ROOT), { recursive: true })
fs.mkdirSync(OUTPUT_ROOT)
for (const dir of [SCREENSHOT_DIR, DOWNLOAD_DIR, LOG_DIR]) {
  fs.mkdirSync(dir)
}

function screenshotPath(name) {
  return path.join(SCREENSHOT_DIR, name)
}

function downloadPath(name) {
  return path.join(DOWNLOAD_DIR, name)
}

function sanitizeFileName(name) {
  return name.replace(/[^a-zA-Z0-9._-]+/g, '-')
}

async function screenshot(page, name, label, manifest) {
  const target = screenshotPath(name)
  await page.screenshot({ path: target, fullPage: true })
  manifest.screenshots.push({ label, path: target })
}

async function dismissDevOverlay(page) {
  await page.addStyleTag({
    content: '#webpack-dev-server-client-overlay{display:none!important;pointer-events:none!important;}',
  }).catch(() => null)
  await page.evaluate(() => {
    const overlay = document.getElementById('webpack-dev-server-client-overlay')
    if (overlay) overlay.remove()
  }).catch(() => null)
}

async function waitForShell(page) {
  await page.waitForLoadState('domcontentloaded')
  await page.waitForTimeout(1200)
  await dismissDevOverlay(page)
}

async function loginIfNeeded(page, manifest) {
  await page.goto(`${FRONTEND_BASE_URL}/login?returnUrl=${encodeURIComponent(ROUTES.micro)}`, {
    waitUntil: 'domcontentloaded',
  })
  await waitForShell(page)

  const username = page.locator('#login-username')
  if (await username.isVisible({ timeout: 10_000 }).catch(() => false)) {
    await screenshot(page, '01-login.png', '登录页', manifest)
    await username.fill('admin')
    await page.locator('#login-password').fill('666666')
    await Promise.all([
      page.waitForResponse((response) => response.url().includes('/api/auth/login')).catch(() => null),
      page.locator('button.login-form__button').click(),
    ])
    await waitForShell(page)
    manifest.steps.push('通过真实登录页使用 admin / 666666 登录。')
    return
  }

  await screenshot(page, '01-session-already-authenticated.png', '已有登录态', manifest)
  manifest.steps.push('打开登录页时已存在可用登录态。')
}

async function assertButtonOrder(page, leftText, rightText) {
  const result = await page.evaluate(({ leftText, rightText }) => {
    const buttons = [...document.querySelectorAll('button')]
      .map((button, index) => ({
        index,
        text: button.innerText.trim(),
        className: button.className,
        left: button.getBoundingClientRect().left,
      }))
      .filter((button) => button.text)
    const left = buttons.find((button) => button.text === leftText)
    const right = buttons.find((button) => button.text === rightText)
    return {
      buttons,
      left,
      right,
      ok: Boolean(left && right && left.left < right.left && /enhance/i.test(left.className)),
    }
  }, { leftText, rightText })
  if (!result.ok) {
    throw new Error(`Button order/style mismatch for ${leftText}/${rightText}: ${JSON.stringify(result)}`)
  }
}

async function openGenerator(page, route, nextButtonText, entryScreenshotName, entryLabel, dialogScreenshotName, dialogLabel, manifest) {
  await page.goto(`${FRONTEND_BASE_URL}${route}`, { waitUntil: 'domcontentloaded' })
  await waitForShell(page)
  await page.getByRole('button', { name: '生成项目' }).waitFor({ state: 'visible', timeout: 30_000 })
  await assertButtonOrder(page, '生成项目', nextButtonText)
  await screenshot(page, entryScreenshotName, entryLabel, manifest)
  await page.getByRole('button', { name: '生成项目' }).click()
  await page.locator('form.cxd-Form').waitFor({ state: 'visible', timeout: 30_000 })
  await expectText(page, ['项目模板', '只展示当前平台版本兼容的官方模板。'])
  await expectTextAbsent(page, ['当前没有可用的官方项目模板'])
  await screenshot(page, dialogScreenshotName, dialogLabel, manifest)
}

async function expectText(page, texts) {
  const bodyText = await page.locator('body').innerText({ timeout: 10_000 })
  for (const text of texts) {
    if (!bodyText.includes(text)) {
      throw new Error(`Expected page text not found: ${text}`)
    }
  }
}

async function expectTextAbsent(page, texts) {
  const bodyText = await page.locator('body').innerText({ timeout: 10_000 })
  for (const text of texts) {
    if (bodyText.includes(text)) {
      throw new Error(`Unexpected page text found: ${text}`)
    }
  }
}

async function chooseAmisOption(page, fieldName, optionText) {
  const control = page.locator(`[data-amis-name="${fieldName}"]:visible`).last()
  await control.waitFor({ state: 'visible', timeout: 10_000 })
  const option = control.getByText(optionText, { exact: true }).last()
  await option.waitFor({ state: 'visible', timeout: 10_000 })
  await option.click()
  await page.waitForTimeout(500)
}

async function fillVisibleInput(page, name, value) {
  const input = page.locator(`input[name="${name}"]:visible`).last()
  await input.waitFor({ state: 'visible', timeout: 10_000 })
  await input.fill(value)
}

async function setVisibleSwitch(page, name, checked) {
  const input = page.locator(`input[name="${name}"]:visible`).last()
  if (!(await input.count())) {
    return
  }
  const isChecked = await input.isChecked()
  if (isChecked !== checked) {
    await input.setChecked(checked)
  }
}

async function tryUiDownload(page, label, manifest) {
  const button = page.getByRole('button', { name: '生成并下载' }).last()
  await button.waitFor({ state: 'visible', timeout: 10_000 })
  const downloadPromise = page.waitForEvent('download', { timeout: 12_000 }).catch(() => null)
  await button.click()
  const download = await downloadPromise
  if (!download) {
    manifest.downloadAttempts.push({ label, method: 'ui-download', status: 'unavailable', reason: 'No browser download event' })
    manifest.steps.push(`${label}：已点击 AMIS 表单提交，浏览器未抛出 download 事件，改用同接口下载 ZIP。`)
    return null
  }
  const suggestedName = sanitizeFileName(download.suggestedFilename())
  const target = downloadPath(suggestedName)
  await download.saveAs(target)
  manifest.downloadAttempts.push({ label, method: 'ui-download', status: 'passed', path: target })
  manifest.downloads.push({ label, method: 'ui-download', suggestedName, path: target })
  manifest.steps.push(`${label}：通过 AMIS 表单触发浏览器下载。`)
  return target
}

async function closeDialog(page) {
  const toast = page.locator('.cxd-Toast:visible').last()
  if (await toast.count()) {
    await toast.waitFor({ state: 'hidden', timeout: 10_000 })
  }
  const cancel = page.getByRole('button', { name: '取消' }).last()
  if (await cancel.isVisible({ timeout: 2000 }).catch(() => false)) {
    await cancel.click()
    await page.waitForTimeout(500)
  }
}

async function loginForApi() {
  const response = await fetch(`${BACKEND_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '666666' }),
  })
  if (!response.ok) {
    throw new Error(`API login failed: HTTP ${response.status}`)
  }
  const body = await response.json()
  const token = body?.data?.accessToken?.token
  if (!token) {
    throw new Error('API login did not return data.accessToken.token')
  }
  return token
}

async function fetchCatalog(token, scope) {
  const response = await fetch(`${BACKEND_BASE_URL}${projectGenerationApiBase(scope)}/project-templates?scope=${scope}&includePreview=false`, {
    headers: { authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw new Error(`Catalog request failed for ${scope}: HTTP ${response.status}`)
  }
  const body = await response.json()
  if (body.code !== 0 || !Array.isArray(body.data?.templates)) {
    throw new Error(`Catalog request failed for ${scope}: code ${body.code}: ${body.message || 'Missing templates'}`)
  }
  return body.data
}

async function generateViaApi({ token, scope, templateId, parameters, outputName, label, manifest }) {
  const catalog = await fetchCatalog(token, scope)
  const template = catalog.templates.find((item) => item.templateId === templateId)
  if (!template) {
    throw new Error(`Template ${templateId} not found for ${scope}`)
  }
  const payload = {
    scope,
    catalogRevision: catalog.revision,
    targetPlatformVersion: catalog.targetPlatformVersion,
    allowPreview: false,
    template: {
      templateId: template.templateId,
      templateVersion: template.templateVersion,
      compatibilityRowId: template.compatibilityRowId,
    },
    ...parameters,
  }
  const response = await fetch(`${BACKEND_BASE_URL}${projectGenerationApiBase(scope)}/project-generations`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    throw new Error(`Generate request failed for ${templateId}: HTTP ${response.status} ${await response.text()}`)
  }
  const buffer = Buffer.from(await response.arrayBuffer())
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/zip') || buffer.slice(0, 4).toString('hex') !== '504b0304') {
    throw new Error(`Generate request for ${templateId} did not return a zip: ${contentType} ${buffer.toString('utf8', 0, 300)}`)
  }
  const target = downloadPath(outputName)
  fs.writeFileSync(target, buffer)
  manifest.downloads.push({ label, method: 'api-download', suggestedName: outputName, path: target, size: buffer.length })
  manifest.downloadAttempts.push({ label, method: 'api-download', status: 'passed', path: target })
  return target
}

function projectGenerationApiBase(scope) {
  return scope === 'PLATFORM_COMPONENT_LIBRARY' ? '/api' : `/api/dev/${APP_NAME}`
}

async function verifyZipContents(zipPath, assertions, manifest) {
  const listOutput = childProcess.execFileSync('unzip', ['-Z1', zipPath], { encoding: 'utf8' })
  const entries = listOutput.split('\n').filter(Boolean)
  for (const mustInclude of assertions.includes || []) {
    if (!entries.some((entry) => entry.includes(mustInclude))) {
      throw new Error(`${zipPath} missing expected entry containing ${mustInclude}`)
    }
  }
  const textEntries = entries
    .filter((entry) => /\.(json|js|jsx|ts|tsx|vue|java|xml|md|yml|yaml|properties)$/.test(entry))
    .map((entry) => ({
      name: entry,
      text: childProcess.execFileSync('unzip', ['-p', zipPath, entry], { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 }),
    }))
  const combined = textEntries.map((entry) => `\n--- ${entry.name} ---\n${entry.text}`).join('\n')
  for (const mustContain of assertions.contains || []) {
    if (!combined.includes(mustContain)) {
      throw new Error(`${zipPath} missing expected text: ${mustContain}`)
    }
  }
  for (const mustNotContain of assertions.notContains || []) {
    if (combined.includes(mustNotContain)) {
      throw new Error(`${zipPath} contains forbidden text: ${mustNotContain}`)
    }
  }
  if ((assertions.packageDependencies || assertions.forbiddenPackageDependencies) && entries.some((entry) => entry.endsWith('/package.json'))) {
    const packageEntry = entries.find((entry) => entry.endsWith('/package.json'))
    const packageJson = JSON.parse(childProcess.execFileSync('unzip', ['-p', zipPath, packageEntry], { encoding: 'utf8' }))
    const dependencies = {
      ...(packageJson.dependencies || {}),
      ...(packageJson.devDependencies || {}),
      ...(packageJson.peerDependencies || {}),
    }
    for (const dependency of assertions.packageDependencies || []) {
      if (!Object.prototype.hasOwnProperty.call(dependencies, dependency)) {
        throw new Error(`${zipPath} missing expected package dependency: ${dependency}`)
      }
    }
    for (const dependency of assertions.forbiddenPackageDependencies || []) {
      if (Object.prototype.hasOwnProperty.call(dependencies, dependency)) {
        throw new Error(`${zipPath} contains forbidden package dependency: ${dependency}`)
      }
    }
  }
  manifest.zipChecks.push({
    path: zipPath,
    entryCount: entries.length,
    checked: assertions.label,
  })
}

async function ensureDownload(uiDownloadPath, apiDownloadArgs) {
  if (uiDownloadPath) {
    return uiDownloadPath
  }
  return generateViaApi(apiDownloadArgs)
}

async function run() {
  const manifest = {
    startedAt: new Date().toISOString(),
    status: 'running',
    repoRoot: REPO_ROOT,
    outputRoot: OUTPUT_ROOT,
    backendBaseUrl: BACKEND_BASE_URL,
    frontendBaseUrl: FRONTEND_BASE_URL,
    screenshots: [],
    downloads: [],
    downloadAttempts: [],
    zipChecks: [],
    templateCatalogs: {},
    steps: [],
    consoleMessages: [],
    requestFailures: [],
  }

  let browser
  try {
    const apiToken = await loginForApi()
    for (const scope of ['MICRO_FRONTEND', 'PLATFORM_COMPONENT_LIBRARY', 'BACKEND_EXTENSION']) {
      const catalog = await fetchCatalog(apiToken, scope)
      manifest.templateCatalogs[scope] = catalog.templates.map((item) => ({
        templateId: item.templateId,
        title: item.title,
        parameters: Object.keys(item.parameterSchema?.properties || {}),
      }))
    }

    browser = await chromium.launch({ headless: true })
    const context = await browser.newContext({
      acceptDownloads: true,
      viewport: { width: 1440, height: 1000 },
      deviceScaleFactor: 1,
    })
    const page = await context.newPage()

    page.on('console', (message) => {
      if (['error', 'warning'].includes(message.type())) {
        manifest.consoleMessages.push({
          type: message.type(),
          text: message.text(),
        })
      }
    })
    page.on('requestfailed', (request) => {
      manifest.requestFailures.push({
        url: request.url(),
        method: request.method(),
        failure: request.failure()?.errorText || '',
      })
    })

    await loginIfNeeded(page, manifest)

    await openGenerator(
      page,
      ROUTES.micro,
      '新建',
      '02-micro-frontend-entry.png',
      '微前端入口界面',
      '03-micro-frontend-dialog.png',
      '微前端 AMIS 生成项目弹窗',
      manifest
    )
    await expectText(page, ['组件库', '自定义布局', '自定义登录页', '前端应用'])
    await screenshot(page, '04-micro-frontend-template-selection.png', '微前端模板选择过程', manifest)
    await chooseAmisOption(page, 'template', '前端应用')
    await chooseAmisOption(page, 'framework', 'React')
    await chooseAmisOption(page, 'buildTool', 'Webpack')
    await fillVisibleInput(page, 'packageName', '@codex/codex-micro-app')
    await setVisibleSwitch(page, 'includeExampleRoute', true)
    await screenshot(page, '05-micro-frontend-options.png', '微前端 React/Webpack 选配过程', manifest)
    const microUiDownload = await tryUiDownload(page, '前端应用 React/Webpack', manifest)
    const microZip = await ensureDownload(microUiDownload, {
      token: apiToken,
      scope: 'MICRO_FRONTEND',
      templateId: 'frontend.micro-app',
      outputName: 'codex-micro-app-1.0.0.zip',
      label: '前端应用 React/Webpack',
      manifest,
      parameters: {
        framework: 'react',
        buildTool: 'webpack',
        packageName: '@codex/codex-micro-app',
        includeExampleRoute: true,
      },
    })
    await screenshot(page, '06-micro-frontend-downloaded.png', '微前端生成下载完成', manifest)
    await closeDialog(page)

    await openGenerator(
      page,
      ROUTES.backend,
      '上传',
      '07-backend-extension-entry.png',
      '后端扩展包入口界面',
      '08-backend-extension-dialog.png',
      '后端扩展包 AMIS 生成项目弹窗',
      manifest
    )
    await expectText(page, ['后端 Maven 扩展包'])
    await fillVisibleInput(page, 'groupId', 'com.codex.demo')
    await fillVisibleInput(page, 'artifactId', 'codex-spring-extension')
    await fillVisibleInput(page, 'version', '0.1.0-SNAPSHOT')
    await fillVisibleInput(page, 'packageName', 'com.codex.demo.extension')
    await fillVisibleInput(page, 'autoConfigurationClassName', 'CodexExtensionAutoConfiguration')
    await screenshot(page, '09-backend-extension-options.png', '后端扩展包选配过程', manifest)
    const backendUiDownload = await tryUiDownload(page, '后端 Maven 扩展包', manifest)
    const backendZip = await ensureDownload(backendUiDownload, {
      token: apiToken,
      scope: 'BACKEND_EXTENSION',
      templateId: 'backend.extension.spring-autoconfigure',
      outputName: 'codex-spring-extension-1.0.0.zip',
      label: '后端 Maven 扩展包',
      manifest,
      parameters: {
        groupId: 'com.codex.demo',
        artifactId: 'codex-spring-extension',
        version: '0.1.0-SNAPSHOT',
        packageName: 'com.codex.demo.extension',
        autoConfigurationClassName: 'CodexExtensionAutoConfiguration',
      },
    })
    await screenshot(page, '10-backend-extension-downloaded.png', '后端扩展包生成下载完成', manifest)
    await closeDialog(page)

    await openGenerator(
      page,
      ROUTES.platformLibrary,
      '上传组件库',
      '11-platform-component-library-entry.png',
      '平台组件库入口界面',
      '12-platform-component-library-dialog.png',
      '平台组件库 AMIS 生成项目弹窗',
      manifest
    )
    await expectText(page, ['平台组件库', '技术栈', '构建方式'])
    await chooseAmisOption(page, 'framework', 'Vue')
    await chooseAmisOption(page, 'buildTool', 'Vite')
    await fillVisibleInput(page, 'packageName', '@codex/platform-components')
    await screenshot(page, '13-platform-component-library-options.png', '平台组件库 Vue/Vite 选配过程', manifest)
    const platformUiDownload = await tryUiDownload(page, '平台组件库 Vue/Vite', manifest)
    const platformZip = await ensureDownload(platformUiDownload, {
      token: apiToken,
      scope: 'PLATFORM_COMPONENT_LIBRARY',
      templateId: 'frontend.platform-component-library',
      outputName: 'platform-components-1.0.0.zip',
      label: '平台组件库 Vue/Vite',
      manifest,
      parameters: {
        framework: 'vue',
        buildTool: 'vite',
        packageName: '@codex/platform-components',
      },
    })
    await screenshot(page, '14-platform-component-library-downloaded.png', '平台组件库生成下载完成', manifest)

    await verifyZipContents(microZip, {
      label: '前端应用 React/Webpack',
      packageDependencies: ['qiankun', 'ouroboros-sdk'],
      forbiddenPackageDependencies: ['amis'],
    }, manifest)
    await verifyZipContents(platformZip, {
      label: '平台组件库 Vue/Vite',
      packageDependencies: ['qiankun', 'ouroboros-sdk'],
      forbiddenPackageDependencies: ['amis'],
    }, manifest)
    await verifyZipContents(backendZip, {
      label: '后端 Maven 扩展包',
      includes: [
        'AGENTS.md',
        'skills/',
        'pom.xml',
        'CodexExtensionAutoConfiguration.java',
        'AutoConfiguration.imports',
        'spring.factories',
      ],
      contains: [
        '<packaging>jar</packaging>',
        'maven-dependency-plugin',
        'gmavenplus-plugin',
        'maven-compiler-plugin',
        'ouroboros-dependency-maven-plugin',
        'com.codex.demo.extension.CodexExtensionAutoConfiguration',
      ],
    }, manifest)

    manifest.completedAt = new Date().toISOString()
    manifest.status = manifest.downloadAttempts.some((attempt) => attempt.status === 'unavailable')
      ? 'completed-with-api-fallback' : 'passed'
  } catch (error) {
    manifest.status = 'failed'
    manifest.error = error.message
    throw error
  } finally {
    try {
      await browser?.close()
    } finally {
      fs.writeFileSync(path.join(OUTPUT_ROOT, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`)
    }
  }
}

run().catch((error) => {
  console.error(error)
  process.exit(1)
})
