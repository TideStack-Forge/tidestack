import os
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
FIXTURES_ROOT = ROOT / "testing/common/fixtures"
FIXTURE_MANIFEST_NAME = "fixture.yaml"
REPOSITORY_BINDINGS_ENV = "OB_TEST_REPOS"


def _load_manifest(path):
  manifest_path = Path(path)
  return json.loads(manifest_path.read_text())


def _parse_repository_binding(binding):
  if not isinstance(binding, str) or "=" not in binding:
    raise ValueError(f"invalid repository binding: {binding!r}; expected NAME=PATH")

  alias, raw_path = binding.split("=", 1)
  alias = alias.strip()
  raw_path = raw_path.strip()
  if not alias or not raw_path:
    raise ValueError(f"invalid repository binding: {binding!r}; expected NAME=PATH")
  return alias, raw_path


def resolve_repository_bindings(cli_args=None, env=None):
  env = env or os.environ
  bindings = {}

  raw_env_bindings = env.get(REPOSITORY_BINDINGS_ENV)
  if raw_env_bindings:
    parsed = json.loads(raw_env_bindings)
    if not isinstance(parsed, dict):
      raise ValueError(f"{REPOSITORY_BINDINGS_ENV} must be a JSON object")
    for alias, raw_path in parsed.items():
      if not alias or raw_path in (None, "", [], {}):
        raise ValueError(f"{REPOSITORY_BINDINGS_ENV} contains an invalid repository binding")
      bindings[str(alias)] = str(raw_path)

  for binding in getattr(cli_args, "repo", None) or []:
    alias, raw_path = _parse_repository_binding(binding)
    bindings[alias] = raw_path

  return bindings


def fixture_input_definition(fixture_manifest, input_name):
  for input_definition in fixture_manifest.get("inputs") or []:
    if not isinstance(input_definition, dict):
      continue
    if input_definition.get("name") == input_name:
      return input_definition
  return None


def fixture_output_definition(fixture_manifest, output_name):
  for output in fixture_manifest.get("outputs") or []:
    if not isinstance(output, dict):
      continue
    if output.get("name") == output_name:
      return output
  return None


def fixture_manifest_path(fixture_class, fixture_name):
  return FIXTURES_ROOT / fixture_class / fixture_name / FIXTURE_MANIFEST_NAME


def load_fixture_manifest(fixture_class, fixture_name):
  manifest_path = fixture_manifest_path(fixture_class, fixture_name)
  if not manifest_path.exists():
    raise FileNotFoundError(f"missing fixture manifest: {fixture_class}/{fixture_name}")

  manifest = _load_manifest(manifest_path)
  expected_id = f"{fixture_class}/{fixture_name}"
  actual_id = manifest.get("id")
  if actual_id != expected_id:
    raise ValueError(f"fixture id mismatch: expected {expected_id}, found {actual_id}")

  actual_class = manifest.get("class")
  if actual_class != fixture_class:
    raise ValueError(f"fixture class mismatch: expected {fixture_class}, found {actual_class}")

  enriched = dict(manifest)
  enriched["manifestPath"] = str(manifest_path)
  return enriched


def resolve_scenario_fixture_bundle(scenario_manifest):
  bundle = {}
  for fixture_class, fixture_names in (scenario_manifest.get("fixtures") or {}).items():
    bundle[fixture_class] = [
        load_fixture_manifest(fixture_class, fixture_name)
        for fixture_name in fixture_names or []
    ]
  return bundle


def fixture_input_value(fixture_manifest, input_name, cli_args=None, env=None):
  resolution = fixture_input_resolution(
      fixture_manifest,
      input_name,
      cli_args=cli_args,
      env=env,
  )
  return resolution.get("value")


def fixture_input_resolution(fixture_manifest, input_name, cli_args=None, env=None):
  env = env or os.environ
  input_definition = fixture_input_definition(fixture_manifest, input_name)
  if not input_definition:
    return {
        "value": None,
        "source": None,
        "source_label": "",
    }

  explicit_value = input_definition.get("value")
  if explicit_value not in (None, "", [], {}):
    return {
        "value": explicit_value,
        "source": {
            "kind": "value",
        },
        "source_label": "fixture-local value",
    }

  repository_bindings = None
  for source in input_definition.get("sources") or []:
    if not isinstance(source, dict):
      continue
    source_kind = source.get("kind")
    if source_kind == "env":
      env_name = source.get("name")
      value = env.get(env_name) if env_name else None
    elif source_kind == "cli" and cli_args is not None:
      cli_field = source.get("field")
      value = getattr(cli_args, cli_field, None) if cli_field else None
    elif source_kind == "repo":
      if repository_bindings is None:
        repository_bindings = resolve_repository_bindings(cli_args=cli_args, env=env)
      repo_name = source.get("name")
      value = repository_bindings.get(repo_name) if repo_name else None
      join_value = source.get("join")
      if value not in (None, "", [], {}) and join_value not in (None, "", [], {}):
        value = str(Path(value) / str(join_value))
    else:
      value = None

    if value not in (None, "", [], {}):
      source_label = source.get("label")
      if not source_label:
        if source_kind == "env" and source.get("name"):
          source_label = source.get("name")
        elif source_kind == "cli" and source.get("field"):
          source_label = f"--{source.get('field').replace('_', '-')}"
        elif source_kind == "repo" and source.get("name"):
          source_label = f"--repo {source.get('name')}=PATH"
        else:
          source_label = source_kind or "unknown-source"
      return {
          "value": value,
          "source": source,
          "source_label": source_label,
      }

  default_value = input_definition.get("default")
  if default_value not in (None, "", [], {}):
    return {
        "value": default_value,
        "source": {
            "kind": "default",
        },
        "source_label": "fixture-local default",
    }
  return {
      "value": None,
      "source": None,
      "source_label": "",
  }


def fixture_output_value(fixture_manifest, output_name, cli_args=None, env=None, _resolving=None):
  env = env or os.environ
  output_definition = fixture_output_definition(fixture_manifest, output_name)
  if not output_definition:
    return None

  explicit_value = output_definition.get("value")
  if explicit_value not in (None, "", [], {}):
    return explicit_value

  env_name = output_definition.get("env")
  if env_name:
    env_value = env.get(env_name)
    if env_value not in (None, "", [], {}):
      return env_value

  from_input = output_definition.get("fromInput")
  if from_input:
    input_value = fixture_input_value(fixture_manifest, from_input, cli_args=cli_args, env=env)
    if input_value not in (None, "", [], {}):
      return input_value

  from_output = output_definition.get("fromOutput")
  if from_output:
    resolving = _resolving or set()
    if output_name in resolving:
      raise ValueError(f"fixture output resolution cycle detected: {output_name}")
    base_value = fixture_output_value(
        fixture_manifest,
        from_output,
        cli_args=cli_args,
        env=env,
        _resolving=resolving | {output_name},
    )
    if base_value in (None, "", [], {}):
      return None
    join_value = output_definition.get("join")
    if join_value in (None, "", [], {}):
      return base_value
    return str(Path(base_value) / str(join_value))

  return None


def first_fixture_output(fixture_bundle, fixture_class, output_name, cli_args=None, env=None):
  for fixture_manifest in fixture_bundle.get(fixture_class, []):
    value = fixture_output_value(
        fixture_manifest,
        output_name,
        cli_args=cli_args,
        env=env,
    )
    if value not in (None, "", [], {}):
      return value
  return None
