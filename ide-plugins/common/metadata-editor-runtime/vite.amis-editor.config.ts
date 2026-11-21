import { createRequire } from 'node:module'
import { basename, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'

const require = createRequire(import.meta.url)
const configDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(configDir, '../../..')
const sharedAmisSrc = resolve(repoRoot, 'frontends/web/ouroboros-amis-runtime/src')
const shimSrc = resolve(configDir, 'src/adapters/amis/ideRuntimeShims')
const { createHostBindings } = require('../../../frontends/web/ouroboros-amis-runtime/host-bindings.cjs')
function packageRootFromEntry(packageName: string, entry: string) {
  const parts = packageName.split('/')
  let current = dirname(entry)
  while (current !== dirname(current)) {
    const matched = parts.length === 1
      ? basename(current) === parts[0]
      : basename(current) === parts[1] && basename(dirname(current)) === parts[0]
    if (matched) {
      return current
    }
    current = dirname(current)
  }
  return dirname(entry)
}

function packageRoot(packageName: string) {
  return packageRootFromEntry(packageName, require.resolve(packageName))
}

const amisRoot = packageRoot('amis')
const amisCoreRoot = packageRoot('amis-core')
const amisEditorRoot = packageRoot('amis-editor')
const amisEditorCoreRoot = packageRoot('amis-editor-core')
const amisThemeEditorHelperRoot = packageRoot('amis-theme-editor-helper')
const amisUiRoot = packageRoot('amis-ui')
const reactRoot = packageRoot('react')
const reactDomRoot = packageRoot('react-dom')
const reactIntersectionObserverRoot = packageRoot('react-intersection-observer')
const reactGridLayoutRoot = packageRoot('react-grid-layout')
const reactResizableRoot = packageRoot('react-resizable')

const hostBindings = createHostBindings({
  'common/eventBus': resolve(shimSrc, 'eventBus.ts'),
  'common/i18n': resolve(shimSrc, 'i18n.ts'),
  'common/i18n/amis': resolve(shimSrc, 'i18nAmis.ts'),
  'common/menus': resolve(shimSrc, 'menus.ts'),
  'common/menus/registry': resolve(shimSrc, 'menuRegistry.ts'),
  'common/micro/commonProps': resolve(shimSrc, 'microCommonProps.ts'),
  'common/micro/entryGuard': resolve(shimSrc, 'microEntryGuard.ts'),
  'common/micro/utils': resolve(shimSrc, 'microUtils.ts'),
  'common/modal': resolve(shimSrc, 'modal.ts'),
  'common/tabs': resolve(shimSrc, 'tabs.ts'),
  'router/routerFacade': resolve(shimSrc, 'routerFacade.ts'),
  'router/utils': resolve(shimSrc, 'routerUtils.ts'),
  'store/storeFacade': resolve(shimSrc, 'storeFacade.ts'),
  'utils/Deferred': resolve(shimSrc, 'Deferred.ts'),
  'utils/Stomp': resolve(shimSrc, 'stomp.ts'),
  'utils/http': resolve(shimSrc, 'http.ts'),
  'utils/statisticsDatasetEditorDirtyGuard': resolve(shimSrc, 'statisticsDatasetEditorDirtyGuard.ts'),
  'utils/url': resolve(shimSrc, 'url.ts')
})

export default defineConfig({
  define: {
    'process.env.NODE_ENV': JSON.stringify('production'),
    'process.env.PLAYWRIGHT_HARNESS': JSON.stringify('false'),
    'process.env.BASE_URL': JSON.stringify(''),
  },
  esbuild: {
    tsconfigRaw: {
      compilerOptions: {
        experimentalDecorators: true,
        jsx: 'react-jsx',
      },
    },
  },
  resolve: {
    dedupe: ['monaco-editor', 'yjs'],
    alias: [
      ...['mobx', 'mobx-react', 'mobx-state-tree', 'downshift', 'monaco-editor-textmate', 'monaco-textmate', 'onigasm', 'yjs'].map(name => ({ find: new RegExp(`^${name}$`), replacement: require.resolve(name) })),
      { find: /^jsonc-parser$/, replacement: resolve(packageRoot('jsonc-parser'), 'lib/esm/main.js') },
      ...Object.entries(hostBindings).sort(([a], [b]) => b.length - a.length).map(([find, replacement]) => ({ find, replacement: replacement as string })),
      { find: /^i18n-runtime$/, replacement: resolve(shimSrc, 'i18nRuntime.ts') },
      { find: /^amis\/lib\/(themes\/.*\.css|helper\.css)$/, replacement: `${amisRoot}/lib/$1` },
      { find: /^amis\/lib\/(.*)$/, replacement: `${amisRoot}/esm/$1` },
      { find: /^amis$/, replacement: `${amisRoot}/esm/index.js` },
      { find: /^amis\/(.*)$/, replacement: `${amisRoot}/$1` },
      { find: /^amis-core\/lib\/(.*)$/, replacement: `${amisCoreRoot}/esm/$1` },
      { find: /^amis-core$/, replacement: `${amisCoreRoot}/esm/index.js` },
      { find: /^amis-core\/(.*)$/, replacement: `${amisCoreRoot}/$1` },
      { find: /^amis-editor\/lib\/(.*)$/, replacement: `${amisEditorRoot}/esm/$1` },
      { find: /^amis-editor$/, replacement: `${amisEditorRoot}/esm/index.js` },
      { find: /^amis-editor\/(.*)$/, replacement: `${amisEditorRoot}/$1` },
      { find: /^amis-editor-core\/lib\/style\.css$/, replacement: `${amisEditorCoreRoot}/lib/style.css` },
      { find: /^amis-editor-core\/lib\/(.*)$/, replacement: `${amisEditorCoreRoot}/esm/$1` },
      { find: /^amis-editor-core$/, replacement: `${amisEditorCoreRoot}/esm/index.js` },
      { find: /^amis-editor-core\/(.*)$/, replacement: `${amisEditorCoreRoot}/$1` },
      { find: /^amis-theme-editor-helper\/lib\/(.*)$/, replacement: `${amisThemeEditorHelperRoot}/esm/$1` },
      { find: /^amis-theme-editor-helper$/, replacement: `${amisThemeEditorHelperRoot}/esm/index.js` },
      { find: /^amis-theme-editor-helper\/(.*)$/, replacement: `${amisThemeEditorHelperRoot}/$1` },
      { find: /^amis-ui\/lib\/(.*)$/, replacement: `${amisUiRoot}/esm/$1` },
      { find: /^amis-ui$/, replacement: `${amisUiRoot}/esm/index.js` },
      { find: /^amis-ui\/(.*)$/, replacement: `${amisUiRoot}/$1` },
      { find: /^react$/, replacement: require.resolve('react') },
      { find: /^react\/(.*)$/, replacement: `${reactRoot}/$1` },
      { find: /^react-dom$/, replacement: require.resolve('react-dom') },
      { find: /^react-dom\/(.*)$/, replacement: `${reactDomRoot}/$1` },
      { find: /^mobx$/, replacement: require.resolve('mobx') },
      { find: /^mobx-react$/, replacement: require.resolve('mobx-react') },
      { find: /^mobx-react-lite$/, replacement: require.resolve('mobx-react-lite') },
      { find: /^mobx-state-tree$/, replacement: require.resolve('mobx-state-tree') },
      { find: /^react-intersection-observer$/, replacement: require.resolve('react-intersection-observer') },
      { find: /^react-intersection-observer\/(.*)$/, replacement: `${reactIntersectionObserverRoot}/$1` },
      { find: /^react-grid-layout$/, replacement: require.resolve('react-grid-layout') },
      { find: /^react-grid-layout\/(.*)$/, replacement: `${reactGridLayoutRoot}/$1` },
      { find: /^react-resizable$/, replacement: require.resolve('react-resizable') },
      { find: /^react-resizable\/(.*)$/, replacement: `${reactResizableRoot}/$1` },
      { find: /^axios$/, replacement: resolve(packageRoot('axios'), 'dist/browser/axios.cjs') },
      { find: /^lodash$/, replacement: require.resolve('lodash') },
      { find: /^lodash\/(.*)$/, replacement: `${packageRoot('lodash')}/$1` },
      { find: /^moment$/, replacement: require.resolve('moment') },
      { find: /^sortablejs$/, replacement: require.resolve('sortablejs') },
      { find: 'exceljs', replacement: resolve(shimSrc, 'exceljs.ts') },
      { find: 'file-saver', replacement: resolve(shimSrc, 'fileSaver.ts') },
      { find: /^qiankun$/, replacement: resolve(shimSrc, 'qiankun.ts') },
      { find: /^qiankun\/lib\/utils$/, replacement: resolve(shimSrc, 'qiankunUtils.ts') },
      { find: /^ouroboros-amis-runtime\/(.*)$/, replacement: `${sharedAmisSrc}/$1` },
    ],
  },
  build: {
    emptyOutDir: true,
    lib: {
      entry: 'src/adapters/amis/amisEditorBrowser.js',
      name: 'OuroborosAmisEditorRuntime',
      formats: ['iife'],
      fileName: () => 'metadata-editor-amis-editor.js',
    },
    cssFileName: 'metadata-editor-amis-editor.css',
    outDir: 'dist/amis-editor',
    minify: false,
  },
})
