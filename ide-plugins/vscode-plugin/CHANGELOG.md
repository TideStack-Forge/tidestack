# Changelog

## [0.3.1] - 2026-11-19

### Fixed

- Code editors remain usable when language services are unavailable; explicit validation reports the service error.
- Late language service failures no longer clear a newer language adapter or revive a disposed editor.
- Metadata validation uses the complete public schemas, including nested fields.

## [0.3.0] - 2026-11-16

### Added

- Online code completion, diagnostics and dynamic editor catalogs using the existing project devKey.
- Local editor service support for Spring Boot applications, test environments and containers through dev-helper IPC.

### Fixed

- Opening AMIS forms and pages no longer adds implicit schema properties.
- Editing dialogs preserves shared references without growing definitions on each save.
- Logic flow node catalogs load in online and offline modes, with visible service errors.
- Approval and other logic flow node icons follow the caller's color.
