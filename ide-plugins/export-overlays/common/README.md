# TideStack Develop Plugin Common

Shared assets and contracts for TideStack Develop IDE plugins.

This repository is intended to be consumed as a Git submodule at `common/` by:

- `tidestack-develop-intellij`
- `tidestack-develop-vsc`

## Contents

- `metadata-editor-runtime/`: shared AMIS editor runtime, browser host, serializer, creation logic, and prebuilt webview runtime assets.
- `metadata-editor-schema/`: pure metadata editor resources, including metadata type contributions, editor registrations, i18n, creation contracts, and asset manifests.
- `amis-sdk/`: AMIS SDK static assets required by embedded IDE webviews.
- `xlsx-runtime/`: XLSX static assets required by embedded IDE webviews.
- `contracts/`: shared IDE contract schemas such as TideStack Develop Config.

`metadata-editor-runtime/vendor/frontends/web/` carries the shared AMIS runtime, SDK and the two embedded editors. `metadata-editor-schema/vendor/` carries the metadata definitions used to generate offline node and field catalogs. Both are exported from the monorepo, without maintaining separate implementations.

## Usage

Clone plugin repositories with submodules:

```bash
git clone --recurse-submodules <plugin-repo-url>
```

Or initialize submodules after cloning:

```bash
git submodule update --init --recursive
```

## Schema maintenance and validation

The monorepo's public `metadata-schema/` directory is the maintained schema source. `metadata-editor-schema/schemas/` contains the complete generated offline bundle, including referenced supporting schemas. Metadata contributions refer to this bundle rather than maintaining inline schema copies.

Install and build the schema package before building the runtime:

```bash
npm ci --prefix metadata-editor-schema
npm run build --prefix metadata-editor-schema
npm ci --prefix metadata-editor-runtime
npm test --prefix metadata-editor-runtime
npm ci --prefix metadata-editor-runtime/vendor/frontends/web/ouroboros-sdk
npm ci --legacy-peer-deps --prefix metadata-editor-runtime/vendor/frontends/web/ouroboros-logicflow-editor
npm ci --legacy-peer-deps --prefix metadata-editor-runtime/vendor/frontends/web/ouroboros-approval-workflow-designer
npm run build --prefix metadata-editor-runtime
```

The schema build generates standalone Ajv validators for the browser and Node hosts; no runtime schema compilation or `unsafe-eval` permission is needed. IntelliJ's fallback editor uses networknt with the same bundled schemas. The shared conformance cases are shipped under `metadata-editor-schema/tests/fixtures/`.
