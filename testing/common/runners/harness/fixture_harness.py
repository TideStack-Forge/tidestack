import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))

from testing.common.runners.fixture_preflight import (  # noqa: E402
    collect_fixture_preflight_failures,
    preflight_fixture_bundle,
    resolve_frontend_inputs,
    resolve_runtime_inputs,
)
from testing.common.runners.fixture_registry import resolve_scenario_fixture_bundle  # noqa: E402


DEFAULT_EVIDENCE_ROOT = ROOT / "tmp/testing-evidence/harness"
DEFAULT_SCENARIO = ROOT / "testing/integration/regression/data-api/metadata-high-code-regression/scenario.yaml"
SUPPORTED_ACTIONS = ("up", "check", "reset", "down")
SUPPORTED_MODES = ("plan", "run")


def _load_manifest(path):
  return json.loads(Path(path).read_text())


def _default_report_dir(scenario_manifest, run_label=None):
  scenario_id = scenario_manifest.get("id", "default").replace("/", "-")
  evidence_root = DEFAULT_EVIDENCE_ROOT / scenario_id
  if run_label:
    return evidence_root / run_label
  return evidence_root / time.strftime("%Y%m%d-%H%M%S")


def _command_for_action(fixture_manifest, action):
  lifecycle = fixture_manifest.get("lifecycle") or {}
  command = lifecycle.get(action, "")
  if command is None:
    return {
        "description": "",
        "shell": "",
        "executable": False,
    }
  if isinstance(command, dict):
    shell_command = str(command.get("shell", "")).strip()
    return {
        "description": str(command.get("description", "")).strip(),
        "shell": shell_command,
        "executable": bool(shell_command),
    }

  command_text = str(command).strip()
  if command_text.startswith("shell:"):
    shell_command = command_text[len("shell:"):].strip()
    return {
        "description": "",
        "shell": shell_command,
        "executable": bool(shell_command),
    }

  return {
      "description": command_text,
      "shell": "",
      "executable": False,
  }


def build_action_plan(fixture_bundle, action):
  if action not in SUPPORTED_ACTIONS:
    raise ValueError(f"unsupported action: {action}")

  plan = []
  for fixture_class, fixture_manifests in (fixture_bundle or {}).items():
    for fixture_manifest in fixture_manifests or []:
      command = _command_for_action(fixture_manifest, action)
      if command["executable"]:
        status = "pending"
        reason = ""
      elif command["description"]:
        status = "manual"
        reason = "lifecycle step is documented but not executable"
      else:
        status = "skipped"
        reason = "lifecycle command not declared"
      plan.append(
          {
              "fixture_id": fixture_manifest.get("id", ""),
              "class": fixture_class,
              "action": action,
              "description": command["description"],
              "command": command["shell"],
              "status": status,
              "reason": reason,
          }
      )
  return plan


def execute_action_plan(plan, cwd=None):
  executions = []
  for item in plan:
    execution = dict(item)
    command = item.get("command", "")
    if not command:
      execution["stdout"] = ""
      execution["stderr"] = ""
      execution["exit_code"] = None
      if execution.get("status") == "manual":
        execution["reason"] = "lifecycle step is documented for harness/manual execution"
      executions.append(execution)
      continue

    completed = subprocess.run(
        ["/bin/zsh", "-lc", command],
        cwd=str(cwd or ROOT),
        capture_output=True,
        text=True,
    )
    execution["stdout"] = completed.stdout
    execution["stderr"] = completed.stderr
    execution["exit_code"] = completed.returncode
    execution["status"] = "pass" if completed.returncode == 0 else "fail"
    execution["reason"] = "" if completed.returncode == 0 else f"command exited with {completed.returncode}"
    executions.append(execution)
    if completed.returncode != 0:
      break
  return executions


def _summary(executions, mode, action):
  return {
      "mode": mode,
      "action": action,
      "pass_count": sum(1 for item in executions if item.get("status") == "pass"),
      "fail_count": sum(1 for item in executions if item.get("status") == "fail"),
      "manual_count": sum(1 for item in executions if item.get("status") == "manual"),
      "skipped_count": sum(1 for item in executions if item.get("status") == "skipped"),
  }


def write_harness_report(report_dir, payload):
  report_dir = Path(report_dir)
  report_dir.mkdir(parents=True, exist_ok=True)
  output_path = report_dir / f"fixture-harness-{payload['summary']['action']}.json"
  output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2))
  return output_path


def parse_args(argv=None):
  parser = argparse.ArgumentParser(description="Plan or execute fixture lifecycle actions for a testing scenario")
  parser.add_argument("--scenario", default=str(DEFAULT_SCENARIO))
  parser.add_argument("--action", choices=SUPPORTED_ACTIONS, required=True)
  parser.add_argument("--mode", choices=SUPPORTED_MODES, default="plan")
  parser.add_argument("--run-label")
  parser.add_argument("--report-dir")
  parser.add_argument("--base-url")
  parser.add_argument("--app-base-url")
  parser.add_argument("--repo", action="append", default=[], help="repository binding in NAME=PATH form; repeat for multiple repositories")
  parser.add_argument("--skip-preflight", action="store_true")
  return parser.parse_args(argv)


def main(argv=None):
  cli_args = parse_args(argv)
  scenario_manifest = _load_manifest(cli_args.scenario)
  try:
    fixture_bundle = resolve_scenario_fixture_bundle(scenario_manifest)
  except (FileNotFoundError, ValueError) as exc:
    raise SystemExit(str(exc))

  runtime_inputs = resolve_runtime_inputs(fixture_bundle, cli_args, os.environ)
  frontend_inputs = resolve_frontend_inputs(fixture_bundle, os.environ)
  fixture_preflight = preflight_fixture_bundle(
      fixture_bundle,
      runtime_inputs,
      frontend_inputs,
      cli_args=cli_args,
      env=os.environ,
  )
  preflight_failures = collect_fixture_preflight_failures(fixture_preflight)
  report_dir = Path(cli_args.report_dir) if cli_args.report_dir else _default_report_dir(scenario_manifest, cli_args.run_label)
  plan = build_action_plan(fixture_bundle, cli_args.action)

  if preflight_failures and not cli_args.skip_preflight:
    payload = {
        "scenarioId": scenario_manifest.get("id", ""),
        "fixturePreflight": fixture_preflight,
        "plan": plan,
        "executions": [],
        "summary": {
            "mode": cli_args.mode,
            "action": cli_args.action,
            "pass_count": 0,
            "fail_count": len(preflight_failures),
            "manual_count": sum(1 for item in plan if item.get("status") == "manual"),
            "skipped_count": sum(1 for item in plan if item.get("status") == "skipped"),
            "blocked": True,
            "blocker": "fixture-preflight-failed",
        },
    }
    write_harness_report(report_dir, payload)
    message = "; ".join(
        f"{failure.get('fixture_id')}: {failure.get('message')}"
        for failure in preflight_failures
    )
    raise SystemExit(f"fixture preflight failed: {message}")

  if cli_args.mode == "plan":
    payload = {
        "scenarioId": scenario_manifest.get("id", ""),
        "fixturePreflight": fixture_preflight,
        "plan": plan,
        "executions": [],
        "summary": _summary(plan, cli_args.mode, cli_args.action),
    }
    write_harness_report(report_dir, payload)
    print(f"fixture harness plan generated: {len(plan)} entries")
    return 0

  executions = execute_action_plan(plan)
  payload = {
      "scenarioId": scenario_manifest.get("id", ""),
      "fixturePreflight": fixture_preflight,
      "plan": plan,
      "executions": executions,
      "summary": _summary(executions, cli_args.mode, cli_args.action),
  }
  write_harness_report(report_dir, payload)
  if any(item.get("status") == "fail" for item in executions):
    print("fixture harness execution failed")
    return 1
  print(f"fixture harness execution completed: {len(executions)} entries")
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
