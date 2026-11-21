const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const path = require('node:path')
const vscode = require('vscode')
const {VsCodeMetadataFileBridge, MetadataCustomEditorProvider} = require('../../dist/metadataCustomEditor')
const {createMetadataEditorHost, createMetadataEditorSession} = require('ouroboros-metadata-editor')

exports.run = async function () {
  const root = process.env.OB_IDE_TEST_WORKSPACE
  assert.ok(root, 'isolated test workspace is required')
  const corpus = JSON.parse(await fs.readFile(path.resolve(__dirname, '../../../../testing/common/fixtures/data/ide-metadata-workspace/documents.json'), 'utf8'))
  const extension = vscode.extensions.getExtension('TideStackForge.develop')
  assert.ok(extension, 'development extension is installed')
  await extension.activate()
  const bridge = new VsCodeMetadataFileBridge()
  try {
    const file = path.join(root, corpus.menu.fileName)
    const original = JSON.stringify(corpus.menu.value)
    await fs.writeFile(file, original)
    const uri = vscode.Uri.file(file)
    const document = await vscode.workspace.openTextDocument(uri)
    await vscode.window.showTextDocument(document)
    const session = createMetadataEditorSession({host: createMetadataEditorHost(bridge), uri: uri.toString(), metadataType: 'menu'})
    const initial = await session.loadState()
    assert.equal(document.isDirty, false)
    const updated = JSON.stringify({...corpus.menu.value, title: 'Native saved'})
    await session.saveText(updated, initial.document.revision)
    assert.equal(document.isDirty, true)
    assert.equal(document.getText(), updated)
    await assert.rejects(session.saveText(original, initial.document.revision), /Revision conflict/)
    assert.equal(document.getText(), updated)
    await vscode.window.showTextDocument(document, {preserveFocus: false})
    await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup')
    await vscode.commands.executeCommand('undo')
    assert.equal(document.getText(), original)
    await vscode.commands.executeCommand('redo')
    assert.equal(document.getText(), updated)
    assert.equal(await document.save(), true)
    assert.equal(document.isDirty, false)
    assert.equal(await fs.readFile(file, 'utf8'), updated)
    console.log('PASS IDE-NATIVE-007 native dirty/conflict/undo/redo/disk')

    // A successful flush reply comes from the real Webview runtime, so this also checks CSP/assets/bootstrap.
    for (const name of ['page', 'form']) {
      const sample = corpus[name]
      const target = path.join(root, sample.fileName)
      const bytes = JSON.stringify(sample.value)
      await fs.writeFile(target, bytes)
      const targetUri = vscode.Uri.file(target)
      await vscode.commands.executeCommand('vscode.openWith', targetUri, 'tidestack.metadataEditor.uiSchema')
      await waitFor(async () => {
        assert.equal(MetadataCustomEditorProvider.getActiveResource()?.toString(), targetUri.toString())
        await MetadataCustomEditorProvider.flushPendingChanges(targetUri)
      })
      await new Promise(resolve => setTimeout(resolve, 800))
      await MetadataCustomEditorProvider.flushPendingChanges(targetUri)
      const opened = await vscode.workspace.openTextDocument(targetUri)
      assert.equal(opened.getText(), bytes)
      assert.equal(opened.isDirty, false)
      assert.equal(await fs.readFile(target, 'utf8'), bytes)
      await vscode.commands.executeCommand('workbench.action.closeActiveEditor')
      console.log(`PASS IDE-SMOKE-008 ${name}: real Webview loaded and no-op preserved bytes`)
    }
  } finally { bridge.dispose() }
}

async function waitFor(assertion) {
  const deadline = Date.now() + 45000
  let failure
  do {
    try { await assertion(); return } catch (error) { failure = error }
    await new Promise(resolve => setTimeout(resolve, 100))
  } while (Date.now() < deadline)
  throw failure
}
