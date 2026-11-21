import { defineConfig } from 'vite'

export default defineConfig({
  build: {
    emptyOutDir: false,
    commonjsOptions: { include: [/node_modules/, /metadataValidators\.cjs$/] },
    lib: {
      entry: 'src/adapters/browser/browserHost.ts',
      name: 'OuroborosMetadataEditorHostCore',
      formats: ['iife'],
      fileName: () => 'metadata-editor-host.js',
    },
    outDir: 'dist/browser',
    minify: false,
  },
})
