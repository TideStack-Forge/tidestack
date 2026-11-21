const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

function readSchema(fileName) {
  const metadataFileName = fileName.replace(/\.json$/, '.ui-schema.json')
  return JSON.parse(
    fs.readFileSync(
      path.join(
        __dirname,
        'src/main/metadata/dev/{appName}/i18n',
        metadataFileName
      ),
      'utf8'
    )
  ).schema
}

function readUiModel(fileName) {
  const metadataFileName = fileName.replace(/\.json$/, '.ui-model.json')
  return JSON.parse(
    fs.readFileSync(
      path.join(
        __dirname,
        'src/main/metadata/dev/{appName}/i18n',
        metadataFileName
      ),
      'utf8'
    )
  )
}

test('i18n add schema exposes toolbar actions that submit the named form', () => {
  const schema = readSchema('add.json')
  const form = schema.body[0]

  assert.equal(schema.type, 'page')
  assert.ok(Array.isArray(schema.toolbar))
  assert.ok(schema.toolbar.some((action) => action.label === '返回列表'))
  assert.ok(
    schema.toolbar.some(
      (action) =>
        action.label === '创建' &&
        action.onEvent &&
        action.onEvent.click &&
        Array.isArray(action.onEvent.click.actions) &&
        action.onEvent.click.actions.some(
          (innerAction) =>
            innerAction.actionType === 'submit' &&
            innerAction.componentName === 'create-i18n-form'
        )
    )
  )
  assert.equal(form.name, 'create-i18n-form')
  assert.deepEqual(form.actions, [])
})

test('i18n list schema forwards keyword filters to the search api', () => {
  const schema = readSchema('../i18n.json')
  const crud = schema.body[0]

  assert.equal(crud.type, 'crud')
  assert.equal(crud.api.method, 'get')
  assert.match(crud.api.url, /\?keyword=\$\{keyword\}/)
})

test('i18n list schema routes the import dialog away from the dynamic edit page', () => {
  const schema = readSchema('../i18n.json')
  const crud = schema.body[0]
  const importButton = crud.headerToolbar.find((action) => action.label === '导入语言文件')

  assert.equal(importButton.actionType, 'dialog')
  assert.equal(importButton.dialog.url, '/dev/${appName}/i18n/import')
})

test('i18n import dialog schema stays as the import form', () => {
  const schema = readSchema('import.json')

  assert.equal(schema.type, 'form')
  assert.equal(schema.api.method, 'post')
  assert.equal(schema.api.url, '/api/dev/${appName}/i18n/import')
})

test('i18n import dialog ui model registers the static import route', () => {
  const uiModel = readUiModel('import.json')

  assert.equal(uiModel.type, 'amis')
  assert.equal(uiModel.title, '导入语言文件')
  assert.equal(uiModel.path, '/dev/{appName}/i18n/import')
})

test('i18n detail page uses locale query param to avoid clobbering form name field', () => {
  const listSchema = readSchema('../i18n.json')
  const crud = listSchema.body[0]
  const detailButton = crud.columns
    .find((column) => column.type === 'operation')
    .buttons.find((action) => action.label === '详情')
  const detailSchema = readSchema('detail.json')

  assert.equal(detailButton.link, '/dev/${appName}/i18n/detail?locale=${model.locale}')
  assert.equal(detailSchema.initApi.url, '/api/dev/${appName}/i18n/${locale}')
})

test('i18n edit schema exposes toolbar actions that submit the named form', () => {
  const schema = readSchema('{fullName}.json')
  const form = schema.body[0]

  assert.equal(schema.type, 'page')
  assert.ok(Array.isArray(schema.toolbar))
  assert.ok(schema.toolbar.some((action) => action.label === '返回列表'))
  assert.ok(
    schema.toolbar.some(
      (action) =>
        action.label === '保存' &&
        action.onEvent &&
        action.onEvent.click &&
        Array.isArray(action.onEvent.click.actions) &&
        action.onEvent.click.actions.some(
          (innerAction) =>
            innerAction.actionType === 'submit' &&
            innerAction.componentName === 'edit-i18n-form'
        )
    )
  )
  assert.equal(form.name, 'edit-i18n-form')
  assert.deepEqual(form.actions, [])
})

test('i18n edit schema keeps locale in the update payload', () => {
  const schema = readSchema('{fullName}.json')
  const form = schema.body[0]

  assert.deepEqual(form.api.data, {
    locale: '${fullName}',
    '&': '$$',
  })
})
