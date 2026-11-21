#!/usr/bin/env node

const fs = require('node:fs')
const path = require('node:path')
const { zipSync } = require('cross-zip')

const [inputArg, outputArg] = process.argv.slice(2)

if (!inputArg || !outputArg) {
  console.error('Usage: node ./scripts/zip-package.cjs <input-path> <output-zip>')
  process.exit(1)
}

const inputPath = `${path.resolve(process.cwd(), inputArg)}${path.sep}.`
const outputPath = path.resolve(process.cwd(), outputArg)

if (!fs.existsSync(inputPath)) {
  console.error(`Input path not found: ${inputPath}`)
  process.exit(1)
}

let temporaryDirectory
try {
  temporaryDirectory = fs.mkdtempSync(path.join(path.dirname(outputPath), '.logicflow-zip-'))
  const temporaryArchive = path.join(temporaryDirectory, 'editor.zip')
  zipSync(inputPath, temporaryArchive)
  fs.renameSync(temporaryArchive, outputPath)
  console.log(`Created ${outputPath}`)
} catch (error) {
  console.error(error)
  process.exitCode = 1
} finally {
  if (temporaryDirectory) fs.rmSync(temporaryDirectory, { recursive: true, force: true })
}
