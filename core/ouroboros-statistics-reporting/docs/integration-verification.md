# Statistics Reporting Integration Verification

Use these commands when wiring statistics-reporting into aggregate modules so the new family is not skipped silently.

## Aggregate structure checks

Run these before and after aggregate wiring:

```bash
mvn -pl :ouroboros-ability-all-core help:effective-pom
mvn -pl :ouroboros-ability-all-runtime help:effective-pom
mvn -pl :ouroboros-dev-module-all help:effective-pom
```

Expected:

- Before wiring: the effective POM does not contain the target artifact.
- After wiring: the effective POM contains the target artifact in the aggregate dependency model.

Recommended grep targets:

- `ouroboros-statistics-reporting-core`
- `ouroboros-statistics-reporting-runtime`
- `ouroboros-statistics-reporting-dev`

## Required verification commands

```bash
mvn -q -pl :ouroboros-statistics-reporting-core,:ouroboros-statistics-reporting-runtime,:ouroboros-statistics-reporting-dev -am test
mvn -q -pl :ouroboros-ability-all-runtime,:ouroboros-dev-module-all -am test -DskipTests=false
mvn -q -pl platform/ouroboros-develop -am -DskipTests package
cd frontends/web/main-app && npm run test:unit -- tests/unit/src/components/amis/renderers/dataset-sql-editor-runtime.test.js
cd frontends/web/main-app && npm run test:integration -- tests/integration/statistics-reporting/dataset-sql-editor.spec.js
```

These verification commands confirm the new module family passes on its own, is included in the aggregate reactors, and can be packaged through the develop platform.

## Front-end prerequisites

If the worktree has not installed front-end dependencies yet, bootstrap both web workspaces first:

```bash
cd frontends/web/main-app && npx npm@10 ci --legacy-peer-deps
cd frontends/web/ouroboros-approval-workflow-designer && npx npm@10 ci --legacy-peer-deps
```

The Playwright smoke depends on both `main-app` and `ouroboros-approval-workflow-designer` being installable in the current worktree.

## Preview behavior

`Dataset Editor` preview now accepts the current in-memory `dataset` payload from the editor page. This means unsaved SQL edits can be previewed directly without forcing a draft save first, while the existing `datasetName` path still remains as the fallback lookup key.
