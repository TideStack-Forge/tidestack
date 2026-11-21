import json
import os
from pathlib import Path
from urllib import parse
from urllib import error
from urllib import request


ROOT = Path(__file__).resolve().parents[3]

from testing.common.runners.fixture_registry import (  # noqa: E402
    fixture_input_definition,
    fixture_input_resolution,
    fixture_input_value,
    fixture_output_definition,
    fixture_output_value,
)


def _resolve_path(path_value):
  if not isinstance(path_value, str) or not path_value:
    return None
  path = Path(path_value)
  if path.is_absolute():
    return path
  return ROOT / path


def _fixture_name(fixture_manifest):
  fixture_id = fixture_manifest.get("id", "")
  if "/" in fixture_id:
    return fixture_id.split("/", 1)[1]
  return fixture_id


def _check(report, name, status, message):
  report["checks"].append(
      {
          "name": name,
          "status": status,
          "message": message,
      }
  )
  if status == "fail":
    report["status"] = "fail"


def resolve_runtime_inputs(fixture_bundle, cli_args=None, env=None):
  if env is None:
    env = os.environ
  runtime_inputs = {}
  for fixture_manifest in fixture_bundle.get("runtimes", []):
    runtime_kind = fixture_output_value(fixture_manifest, "runtimeKind", cli_args=cli_args, env=env) or _fixture_name(fixture_manifest)
    base_url_env = fixture_output_value(fixture_manifest, "baseUrlEnv", cli_args=cli_args, env=env)
    cli_value = None
    if cli_args:
      if runtime_kind == "develop":
        cli_value = getattr(cli_args, "base_url", None)
      elif runtime_kind == "mothership":
        cli_value = getattr(cli_args, "app_base_url", None)
    runtime_inputs[runtime_kind] = {
        "runtimeKind": runtime_kind,
        "baseUrl": cli_value or (env.get(base_url_env) if base_url_env else None),
        "baseUrlEnv": base_url_env,
    }
  return runtime_inputs


def resolve_frontend_inputs(fixture_bundle, env=None):
  if env is None:
    env = os.environ
  frontend_inputs = {}
  for fixture_manifest in fixture_bundle.get("frontends", []):
    frontend_kind = fixture_output_value(fixture_manifest, "frontendKind", env=env) or _fixture_name(fixture_manifest)
    base_url_env = fixture_output_value(fixture_manifest, "baseUrlEnv", env=env)
    frontend_inputs[frontend_kind] = {
        "frontendKind": frontend_kind,
        "baseUrl": env.get(base_url_env) if base_url_env else None,
        "baseUrlEnv": base_url_env,
    }
  return frontend_inputs


def _base_report(fixture_manifest):
  return {
      "fixture_id": fixture_manifest.get("id", ""),
      "class": fixture_manifest.get("class", ""),
      "title": fixture_manifest.get("title", ""),
      "status": "pass",
      "checks": [],
      "notes": [],
  }


def _preflight_outputs(report, fixture_manifest):
  seen = set()
  for output in fixture_manifest.get("outputs") or []:
    if not isinstance(output, dict):
      _check(report, "output-entry-shape", "fail", "fixture outputs must be object entries")
      continue
    output_name = output.get("name")
    if not output_name:
      _check(report, "output-name-required", "fail", "fixture output is missing name")
      continue
    if output_name in seen:
      _check(report, f"output.{output_name}.unique", "fail", f"duplicate fixture output: {output_name}")
      continue
    seen.add(output_name)
    _check(report, f"output.{output_name}.present", "pass", f"fixture output declared: {output_name}")


def _describe_input_sources(input_definition):
  sources = []
  for source in input_definition.get("sources") or []:
    if not isinstance(source, dict):
      continue
    source_kind = source.get("kind")
    if source_kind == "env" and source.get("name"):
      sources.append(source.get("name"))
    elif source_kind == "cli" and source.get("field"):
      label = source.get("label") or f"--{source.get('field').replace('_', '-')}"
      sources.append(label)
    elif source_kind == "repo" and source.get("name"):
      label = source.get("label") or f"--repo {source.get('name')}=PATH"
      sources.append(label)
  return sources


def _preflight_input_resolution(report, fixture_manifest, input_name, cli_args=None, env=None):
  input_definition = fixture_input_definition(fixture_manifest, input_name)
  if not input_definition:
    return None

  resolution = fixture_input_resolution(fixture_manifest, input_name, cli_args=cli_args, env=env)
  value = resolution.get("value")
  sources = _describe_input_sources(input_definition)
  source_text = ", ".join(sources) if sources else "fixture-local default"
  resolved_from = resolution.get("source_label") or source_text
  if value not in (None, "", [], {}):
    _check(report, f"input.{input_name}.resolved", "pass", f"{input_name} resolved from {resolved_from}")
    return value

  if input_definition.get("required"):
    _check(
        report,
        f"input.{input_name}.resolved",
        "fail",
        f"required fixture input {input_name} is unresolved; expected one of: {source_text}",
    )
  else:
    report["notes"].append(
        f"fixture input {input_name} is unresolved; external injection is expected from {source_text}"
    )
  return None


def _preflight_data_fixture(report, fixture_manifest, cli_args=None, env=None):
  app_root = _preflight_input_resolution(report, fixture_manifest, "appRoot", cli_args=cli_args, env=env)
  for output_name in ("appRoot", "businessModelDir", "uiSchemaDir"):
    output_definition = fixture_output_definition(fixture_manifest, output_name)
    if not output_definition:
      if output_name == "businessModelDir" and app_root:
        resolved_path = Path(app_root) / "business-model"
      elif output_name == "uiSchemaDir" and app_root:
        resolved_path = Path(app_root) / "ui-schema"
      else:
        continue
    else:
      output_value = fixture_output_value(fixture_manifest, output_name, cli_args=cli_args, env=env)
      if not output_value:
        continue
      resolved_path = _resolve_path(output_value)
    if resolved_path.exists():
      _check(report, f"data.{output_name}.exists", "pass", f"{resolved_path} exists")
    else:
      _check(report, f"data.{output_name}.exists", "fail", f"{resolved_path} does not exist")


def _preflight_identity_fixture(report, fixture_manifest):
  login_payload = fixture_output_value(fixture_manifest, "loginPayload")
  if login_payload is None:
    report["notes"].append("identity fixture does not embed loginPayload; external credential injection is expected")
    return

  if not isinstance(login_payload, dict):
    _check(report, "identity.loginPayload.shape", "fail", "loginPayload must be an object")
    return

  missing_fields = [field_name for field_name in ("account", "password") if not login_payload.get(field_name)]
  if missing_fields:
    _check(
        report,
        "identity.loginPayload.required-fields",
        "fail",
        "loginPayload is missing required fields: " + ", ".join(missing_fields),
    )
    return

  _check(report, "identity.loginPayload.required-fields", "pass", "loginPayload contains account/password")


def _probe_http_status(url, token=None):
  headers = {}
  if token:
    headers["Authorization"] = f"Bearer {token}"
  probe_request = request.Request(url, headers=headers)
  try:
    with request.urlopen(probe_request, timeout=3) as response:
      return response.getcode(), None
  except error.HTTPError as exception:
    return exception.code, None
  except OSError as exception:
    return None, exception


def _fetch_admin_access_token(base_url, env):
  injected_token = env.get("OB_TEST_ADMIN_ACCESS_TOKEN")
  if injected_token:
    return injected_token, None

  login_url = parse.urljoin(base_url.rstrip("/") + "/", "api/auth/login")
  account = env.get("OB_TEST_ADMIN_ACCOUNT", "admin")
  password = env.get("OB_TEST_ADMIN_PASSWORD", "666666")
  payload = json.dumps({"account": account, "password": password}).encode("utf-8")
  login_request = request.Request(
      login_url,
      data=payload,
      headers={"Content-Type": "application/json"},
      method="POST",
  )
  try:
    with request.urlopen(login_request, timeout=3) as response:
      response_payload = json.loads(response.read().decode("utf-8"))
  except (OSError, ValueError, json.JSONDecodeError) as exception:
    return None, str(exception)

  if not isinstance(response_payload, dict):
    return None, "login response must be an object"

  response_data = response_payload.get("data")
  if not isinstance(response_data, dict):
    response_code = response_payload.get("code")
    response_message = response_payload.get("msg") or "unknown login failure"
    return None, f"login failed (code={response_code}): {response_message}"

  access_token = response_data.get("accessToken")
  token = access_token.get("token") if isinstance(access_token, dict) else None
  if not token:
    return None, "login response did not contain data.accessToken.token"
  return token, None


def _preflight_runtime_fixture(report, fixture_manifest, runtime_inputs, env=None):
  env = env or os.environ
  runtime_kind = fixture_output_value(fixture_manifest, "runtimeKind") or _fixture_name(fixture_manifest)
  runtime_input = runtime_inputs.get(runtime_kind, {})
  base_url = runtime_input.get("baseUrl")
  base_url_env = runtime_input.get("baseUrlEnv")
  unauthenticated_probe = fixture_output_value(fixture_manifest, "unauthenticatedProbe")
  health_probe = fixture_output_value(fixture_manifest, "healthProbe")

  if not base_url_env:
    _check(report, f"runtime.{runtime_kind}.base-url-env", "fail", "runtime fixture must declare baseUrlEnv output")
  else:
    _check(report, f"runtime.{runtime_kind}.base-url-env", "pass", f"runtime base URL env: {base_url_env}")

  if unauthenticated_probe:
    if isinstance(unauthenticated_probe, str) and unauthenticated_probe.startswith("/"):
      _check(
          report,
          f"runtime.{runtime_kind}.probe-shape",
          "pass",
          f"runtime unauthenticated probe path: {unauthenticated_probe}",
      )
    else:
      _check(
          report,
          f"runtime.{runtime_kind}.probe-shape",
          "fail",
          "runtime unauthenticatedProbe must be an absolute path starting with /",
      )

  if health_probe:
    if isinstance(health_probe, str) and health_probe.startswith("/"):
      _check(
          report,
          f"runtime.{runtime_kind}.health-probe-shape",
          "pass",
          f"runtime health probe path: {health_probe}",
      )
    else:
      _check(
          report,
          f"runtime.{runtime_kind}.health-probe-shape",
          "fail",
          "runtime healthProbe must be an absolute path starting with /",
      )

  if not base_url:
    report["notes"].append(
        f"runtime {runtime_kind} did not receive baseUrl; live HTTP preflight will be skipped until CLI or env provides it"
    )
    return

  parsed = parse.urlparse(base_url)
  if parsed.scheme not in ("http", "https") or not parsed.netloc:
    _check(report, f"runtime.{runtime_kind}.base-url-shape", "fail", f"invalid runtime baseUrl: {base_url}")
    return

  _check(report, f"runtime.{runtime_kind}.base-url-shape", "pass", f"resolved runtime baseUrl: {base_url}")

  if health_probe:
    health_url = parse.urljoin(base_url.rstrip("/") + "/", health_probe.lstrip("/"))
    status, probe_error = _probe_http_status(health_url)
    if probe_error:
      _check(
          report,
          f"runtime.{runtime_kind}.health-live",
          "fail",
          f"{health_url} is unreachable: {probe_error}",
      )
      return

    if status == 401:
      token, token_error = _fetch_admin_access_token(base_url, env)
      if not token:
        _check(
            report,
            f"runtime.{runtime_kind}.health-live",
            "fail",
            f"{health_url} returned 401 and authenticated health retry could not get admin token: {token_error}",
        )
        return
      status, probe_error = _probe_http_status(health_url, token=token)
      if probe_error:
        _check(
            report,
            f"runtime.{runtime_kind}.health-live",
            "fail",
            f"{health_url} authenticated retry is unreachable: {probe_error}",
        )
        return
      if status == 200:
        _check(
            report,
            f"runtime.{runtime_kind}.health-live",
            "pass",
            f"{health_url} returned 200 with authenticated actuator health probe",
        )
        return

    if status == 200:
      _check(report, f"runtime.{runtime_kind}.health-live", "pass", f"{health_url} returned 200")
    elif status == 503:
      _check(
          report,
          f"runtime.{runtime_kind}.health-live",
          "fail",
          f"{health_url} returned 503; lifecycle is not ready",
      )
    else:
      _check(report, f"runtime.{runtime_kind}.health-live", "fail", f"{health_url} returned {status}")


def _preflight_frontend_fixture(report, fixture_manifest, frontend_inputs):
  frontend_kind = fixture_output_value(fixture_manifest, "frontendKind") or _fixture_name(fixture_manifest)
  frontend_input = frontend_inputs.get(frontend_kind, {})
  base_url = frontend_input.get("baseUrl")
  base_url_env = frontend_input.get("baseUrlEnv")
  entry_route = fixture_output_value(fixture_manifest, "entryRoute")

  if not base_url_env:
    _check(report, f"frontend.{frontend_kind}.base-url-env", "fail", "frontend fixture must declare baseUrlEnv output")
  else:
    _check(report, f"frontend.{frontend_kind}.base-url-env", "pass", f"frontend base URL env: {base_url_env}")

  if entry_route:
    if isinstance(entry_route, str) and entry_route.startswith("/"):
      _check(report, f"frontend.{frontend_kind}.entry-route-shape", "pass", f"frontend entry route: {entry_route}")
    else:
      _check(report, f"frontend.{frontend_kind}.entry-route-shape", "fail", "frontend entryRoute must start with /")

  if not base_url:
    report["notes"].append(
        f"frontend {frontend_kind} did not receive baseUrl; browser preflight will stay in harness/manual mode until env provides it"
    )
    return

  parsed = parse.urlparse(base_url)
  if parsed.scheme not in ("http", "https") or not parsed.netloc:
    _check(report, f"frontend.{frontend_kind}.base-url-shape", "fail", f"invalid frontend baseUrl: {base_url}")
    return

  _check(report, f"frontend.{frontend_kind}.base-url-shape", "pass", f"resolved frontend baseUrl: {base_url}")


def preflight_fixture_bundle(fixture_bundle, runtime_inputs=None, frontend_inputs=None, cli_args=None, env=None):
  runtime_inputs = runtime_inputs or {}
  frontend_inputs = frontend_inputs or {}
  env = env or os.environ
  reports = []
  for fixture_class, fixture_manifests in (fixture_bundle or {}).items():
    for fixture_manifest in fixture_manifests or []:
      report = _base_report(fixture_manifest)
      _preflight_outputs(report, fixture_manifest)
      if fixture_class == "data":
        _preflight_data_fixture(report, fixture_manifest, cli_args=cli_args, env=env)
      elif fixture_class == "identities":
        _preflight_identity_fixture(report, fixture_manifest)
      elif fixture_class == "runtimes":
        _preflight_runtime_fixture(report, fixture_manifest, runtime_inputs, env=env)
      elif fixture_class == "frontends":
        _preflight_frontend_fixture(report, fixture_manifest, frontend_inputs)
      else:
        report["notes"].append(f"no specialized preflight registered for fixture class {fixture_class}")
      reports.append(report)
  return reports


def collect_fixture_preflight_failures(reports):
  failures = []
  for report in reports:
    failed_checks = [check for check in report.get("checks", []) if check.get("status") == "fail"]
    if not failed_checks:
      continue
    failures.append(
        {
            "fixture_id": report.get("fixture_id", ""),
            "message": "; ".join(check.get("message", "") for check in failed_checks),
        }
    )
  return failures


def write_fixture_preflight_report(reports, report_dir):
  report_dir = Path(report_dir)
  report_dir.mkdir(parents=True, exist_ok=True)
  output_path = report_dir / "fixture-preflight.json"
  output_path.write_text(
      json.dumps(
          {
              "reportDir": str(report_dir),
              "reports": reports,
          },
          ensure_ascii=False,
          indent=2,
      )
  )
  return output_path
