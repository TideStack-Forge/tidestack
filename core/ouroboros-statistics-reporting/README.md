# Ouroboros Statistics Reporting

`ouroboros-statistics-reporting` provides the application metadata and execution
runtime for reusable `Dataset` definitions and `Chart` definitions. A chart is
stored as a `ChartDefinition`; dashboards and composite views are business
model types and are not independent statistics-reporting assets.

## Module Structure

```
ouroboros-statistics-reporting/
├── ouroboros-statistics-reporting-core/       # metadata definitions and validators
├── ouroboros-statistics-reporting-dev/        # development CRUD, preview and UI schema
└── ouroboros-statistics-reporting-runtime/    # definition loading, execution and projection
```

- `ouroboros-statistics-reporting-core` defines datasets, SQL queries, SQL
  dialects, parameters, output schemas and chart definitions.
- `ouroboros-statistics-reporting-dev` manages application source files and
  development-only preview configuration.
- `ouroboros-statistics-reporting-runtime` loads enabled definitions, executes
  datasets and projects chart results.

## Metadata Boundary

Application metadata is source code. It contains only portable business
definitions:

- `DatasetDefinition`: `name`, display metadata, `isEnabled`, SQL query
  definition, parameters, output schema, permissions and execution limits.
- `SqlQueryDefinition`: `datasource`, common `sql` and `dialects`.
- `SqlDialectDefinition`: only `dialectName` and `sql`.
- Parameters use the `${param}` placeholder syntax.
- `ChartDefinition` references a dataset and stores `chartType` with its
  type-specific `params`.
- Dataset and Chart names use dot-separated segments containing only ASCII letters, digits,
  underscores and hyphens. The same rule applies to saving and runtime loading,
  and identifiers, references and uniqueness are case insensitive.

Dataset parameter definitions are validated as source-code contracts:

- Parameter names must match `[A-Za-z_][A-Za-z0-9_]*` and every `${param}` in
  common or dialect SQL must have a declaration.
- A declared parameter must be referenced by at least one SQL statement. A
  parameter used only by a dialect is valid; the union of all SQL statements
  is the reference set.
- Parameter types are `string`, `number`, `date`, `datetime` and `boolean`.
- Operators are `eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `in`, `notIn` and `like`.
  `in` and `notIn` require `multiValue=true`; `multiValue=true` requires one
  of those operators, and `like` requires a string parameter.
- `allowEmptyOmitCondition` cannot be combined with `required=true`.
  `valueSource=systemContext` requires `systemContextBinding`; a binding is
  not allowed for `valueSource=request`.

Invalid names, placeholders, declarations or parameter combinations are
rejected while saving the Dataset. The runtime JDBC adapter only converts
validated `${param}` placeholders to named JDBC parameters.

JDBC URLs, credentials, environment-specific settings and design-time preview
parameters are platform-side configuration. They are never serialized into the
application metadata files. Git records application metadata changes; the
statistics module has no independent publish state or publish workflow.

## Development Entry Points

The development UI has two direct entries:

- `/dev/{appName}/dataset`
- `/dev/{appName}/chart`

Object pages are `/dev/{appName}/dataset/add`,
`/dev/{appName}/dataset/{datasetName}`, `/dev/{appName}/chart/add` and
`/dev/{appName}/chart/{fullName}`. The corresponding development APIs use the
same direct module paths:

- `/api/dev/{appName}/dataset/**`
- `/api/dev/{appName}/chart/**`

The dataset editor provides SQL and dialect tabs, parameter and output-schema
table editors, schema inference, preview and a development-side connection
configuration action. The chart editor uses a narrow property area and a large
runtime preview area. Dashboard authoring is provided by the business-model
editor for `modelType=dashboard`.

Preview parameters have their own editor beside the table-structure action.
Connection configuration manages only the connection reference. The table browser
supports table search and shows fields for the selected table. The Chart property
area starts with collapsible basic information, followed by collapsible dataset
and field bindings. Basic information is collapsed by default, while dataset and
field bindings are expanded.
Dashboard editing uses a visible toolbar, a grid canvas and persistent component
controls for configuration, styling and removal.

## Runtime API

Dataset execution and export:

- `POST /api/statistics-reporting/datasets/{datasetName}/execute`
- `POST /api/statistics-reporting/datasets/{datasetName}/export`

Chart execution and export:

- `POST /api/runtime/statistics-reporting/charts/{chartName}/execute`
- `POST /api/runtime/statistics-reporting/charts/{chartName}/export`

Dashboard runtime routes are projected by each `DashboardBusinessModel`. The
UI path and structure API path come from the business model full name:

- `demo.salesDashboard` -> UI `/demo/sales-dashboard`
- `demo.salesDashboard` -> API `GET /api/demo/sales-dashboard`

The Dashboard API returns only `DashboardBusinessModelMeta` structure. It does
not execute datasets or embed widget data. The frontend Dashboard renderer uses
each widget's top-level `chartName` to call the Chart execution API independently.

Composite-view runtime is owned by the business-model runtime and uses the
dataset execution service as its read-only execution path.

The runtime routes are fixed controller entry points. Each effective Dataset and
Chart projects separate execute and export authorities through the platform's
`AuthorityProvider` mechanism. Names follow
`statisticsReporting.runtime.dataset.<name>.execute|export` and
`statisticsReporting.runtime.chart.<name>.execute|export`. The route name selects
the definition, including for Dataset requests. Permission paths match identifiers
case insensitively. Composite views project their execute authority through the
standard business-model export. Runtime failures use platform exceptions and the
shared application exception advice.

## Definition Storage

Published application source definitions are loaded from:

- `${app.home}/statistics-reporting/datasets`
- `${app.home}/statistics-reporting/charts`

Runtime overrides, where explicitly supported by the platform, use the
corresponding `data/statistics-reporting` directories. These are source/runtime
loading locations, not development UI routes. Dataset development configuration
remains in the platform typed-model store and is applied only to preview and
inference.

Only `isEnabled=true` definitions participate in runtime execution. Definition
providers return enabled and disabled runtime definitions to the definition
center. The center merges them by provider order: application defaults use 100,
runtime overrides use 200, and higher values replace lower values. A disabled
override masks an enabled default; duplicate names in the same tier are rejected.

Loading uses strict JSON deserialization. Malformed JSON, unknown metadata
fields and missing names raise a platform exception with the file path and
original cause where available. File access failures raise an internal exception.
A failed center refresh preserves its previous snapshot and reports the failure;
it never silently falls back to a lower-priority definition.

## Verification

```bash
mvn -pl :ouroboros-statistics-reporting-core,:ouroboros-statistics-reporting-runtime,:ouroboros-statistics-reporting-dev -am test
mvn -pl :ouroboros-statistics-reporting-dev -am -Dtest=StatisticsReportingUISchemaProviderTest,StatisticsReportingDevControllerWiringTest -Dsurefire.failIfNoSpecifiedTests=false test
cd frontends/web/main-app && npx vitest run tests/unit/src/components/amis/renderers/dataset-sql-editor-runtime.test.js
```

For runtime fixture verification, run
`testing/common/fixtures/data/statistics-reporting-runtime-seed/prepare.sh --check`
with `OB_TEST_APP_ROOT` and a reachable `OB_TEST_BASE_URL`.

## Existing definitions

The one-time conversion and development configuration import procedure is in
[the module design](docs/module-design.md#existing-definitions). Run the conversion
preview before replacing exported application definitions.
