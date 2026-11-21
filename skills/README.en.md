# TideStack Application Development Skills

The public suite has **17 skills**. Three repository-only skills (operate-ouroboros, run-tests, publish-ouroboros) bring the independent total to **20**. Generated template copies do not count as separate skills. Chinese catalog and migration details: [README.md](README.md).

The integration baseline is `dev-next@c3ff6111937767f373392c24fda9f698bc48c068`; snapshots retain their own provenance. TideStack names the skills; keep actual Ouroboros packages, Maven coordinates, SDK, images and metadata paths. Verify the consuming application's actual dependencies, especially SNAPSHOT artifacts.

The public suite serves application developers. Reuse installed modules, services and metadata for ordinary work; read custom key, plugin, decorator and permission-resolver contracts only when those extensions are needed. Platform registries, delivery infrastructure and template-generator maintenance are outside the ordinary application workflow.

## Install

From the platform checkout:

```bash
npx skills add ./skills --list
npx skills add ./skills --all
```

When this directory is the root of a separately published repository, use `npx skills add <owner>/<repo> --all`. Individual installation must include the sibling tasks it actually references. Backend-generated projects receive the portable suite under `.agents/skills/`.

The advisor carries the [capability map and lookup guide](tidestack-howto/references/capability-map.md), a compact index and the full catalog (107 entries). Search the index first, load selected full entries, and report stable IDs. Source-reviewed catalog entries do not prove a deployment has enabled or tested those capabilities.

## Catalog

| Skill | Task |
| --- | --- |
| [tidestack-howto](tidestack-howto/SKILL.md) | Read-only capability selection, implementation boundaries and resource planning; never implements or invokes implementation skills automatically |
| [tidestack-backend-extension-development](tidestack-backend-extension-development/SKILL.md) | Embedded hosts, extension JARs, dependency scopes, registration and delivery |
| [tidestack-frontend-extension-development](tidestack-frontend-extension-development/SKILL.md) | Components, micro apps, layouts, login, SDK and lifecycle integration |
| [tidestack-business-entry-definition](tidestack-business-entry-definition/SKILL.md) | Ownership and references across navigation, authority and UI routes |
| [tidestack-metadata-definition](tidestack-metadata-definition/SKILL.md) | 32 metadata contracts, loaders, full validation and supporting resources |
| [tidestack-metadata-migration](tidestack-metadata-migration/SKILL.md) | Preview and migrate legacy paths, checking conflicts and inherited packaging |
| [tidestack-local-debugging](tidestack-local-debugging/SKILL.md) | Reuse existing environments, start a frontend base when needed, and verify application behavior |
| [tidestack-ui-schema-development](tidestack-ui-schema-development/SKILL.md) | AMIS pages, forms, actions, data scopes and useful UI copy |
| [tidestack-business-model-development](tidestack-business-model-development/SKILL.md) | Business objects, relations, views, actions and Java handlers |
| [tidestack-data-model-development](tidestack-data-model-development/SKILL.md) | Typed models, keys, constraints, queries, transactions, plugins and decorators |
| [tidestack-web-authorization](tidestack-web-authorization/SKILL.md) | Method annotations, API binding, grants and HTTP authorization checks |
| [tidestack-access-control-development](tidestack-access-control-development/SKILL.md) | Row/field definitions, resolvers, configuration forms and actual enforcement |
| [tidestack-business-event-development](tidestack-business-event-development/SKILL.md) | Event publication and subscription, principals, business idempotency and recovery |
| [tidestack-logicflow-development](tidestack-logicflow-development/SKILL.md) | Automatic flows, scripts, nodes and execution/failure contracts |
| [tidestack-approval-integration](tidestack-approval-integration/SKILL.md) | Human workflow binding, initiation, forms, results and business state |
| [tidestack-rpc-integration](tidestack-rpc-integration/SKILL.md) | Outbound models, credentials, mappings, timeout and retry |
| [tidestack-notification-development](tidestack-notification-development/SKILL.md) | Templates, recipients, channels, dispatch and delivery checks |

## Migration and maintenance

The former `tidestack` entry is renamed to `tidestack-howto`. The former platform-development router merges into the read-only tidestack-howto advisor. Backend/frontend development gain the extension-development names; the former UI schema definition task becomes ui-schema-development. Project initialization merges into backend-extension's embedded/standard runtime references, with typed compilation in data-model. Thin module/menu/UI-model/authority/configuration tasks become focused metadata and business-entry references. Duplicate migration and web launchers merge into the public tasks. Install replacements and update callers before removing old user-installed copies.

The public suite is the source for both backend template distributions. All metadata schemas come from the public `metadata-schema/` directory and are distributed identically to the handbook, IDE schema package, Skills and templates, with source paths and hashes recorded. Typed UI resources and component content each have a public contract. Capability JSON files come from the handbook catalog and retain source status and hashes. Run these from the platform checkout:

```bash
python3 scripts/skills/sync_distribution.py
python3 scripts/skills/sync_distribution.py --check
python3 -m unittest discover -s scripts/skills/tests -v
```

Consumers do not need monorepo qualification tools. Metadata's standalone validator requires jsonschema for full Draft 2020-12 validation; an explicitly selected subset pass is not full acceptance. Generated distributions omit maintainer tests and retain the required runtime tools and references.

Custom approval form descriptions: `approval-form` / `.approval-form.json`. The maintained schema is `metadata-schema/approval-form.schema.json`; the existing distribution script synchronizes it to skill references and project templates.
