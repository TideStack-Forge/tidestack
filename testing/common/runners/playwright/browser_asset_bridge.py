import argparse
import json
import subprocess
import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
if str(ROOT) not in sys.path:
  sys.path.insert(0, str(ROOT))


def _load_manifest(path):
  return json.loads(Path(path).read_text())


def _resolve_path(path_value, base_dir=None):
  if not path_value:
    return None
  path = Path(path_value)
  if path.is_absolute():
    return path
  if base_dir is not None:
    return (base_dir / path).resolve()
  return ROOT / path


def _default_report_dir(manifest, run_label=None):
  scenario_id = manifest.get("scenario", "default").replace("/", "-")
  evidence_dir = manifest.get("reports", {}).get("evidenceDir", "tmp/testing-evidence/browser-bridge")
  root = _resolve_path(evidence_dir)
  if run_label:
    return root / run_label
  return root / ("browser-bridge-" + time.strftime("%Y%m%d-%H%M%S"))


def _load_package_json(project_root):
  package_json_path = project_root / "package.json"
  if not package_json_path.exists():
    return None, package_json_path
  return json.loads(package_json_path.read_text()), package_json_path


def _check_item(report, name, status, message):
  report["checks"].append(
      {
          "name": name,
          "status": status,
          "message": message,
      }
  )
  if status == "fail":
    report["status"] = "fail"


def preflight_browser_assets(manifest):
  browser_assets = manifest.get("browserAssets") or {}
  report = {
      "scenario": manifest.get("scenario", ""),
      "status": "pass",
      "checks": [],
      "scripts": [],
      "notes": [],
  }

  project_root = _resolve_path(browser_assets.get("projectRoot"))
  package_json = None
  package_json_path = None
  if project_root:
    if project_root.exists():
      _check_item(report, "project-root.exists", "pass", f"{project_root} exists")
      package_json, package_json_path = _load_package_json(project_root)
      if package_json_path and package_json_path.exists():
        _check_item(report, "package-json.exists", "pass", f"{package_json_path} exists")
      else:
        _check_item(report, "package-json.exists", "fail", f"{package_json_path} does not exist")
      node_modules_path = project_root / "node_modules"
      if node_modules_path.exists():
        _check_item(report, "node-modules.exists", "pass", f"{node_modules_path} exists")
      else:
        report["notes"].append(f"{node_modules_path} is missing")
    else:
      _check_item(report, "project-root.exists", "fail", f"{project_root} does not exist")

  for contract_path_value in browser_assets.get("contracts") or []:
    contract_path = _resolve_path(contract_path_value)
    if contract_path.exists():
      _check_item(report, f"contract.{contract_path.name}", "pass", f"{contract_path} exists")
    else:
      _check_item(report, f"contract.{contract_path.name}", "fail", f"{contract_path} does not exist")

  for source_path_value in browser_assets.get("sources") or []:
    source_path = _resolve_path(source_path_value)
    if source_path.exists():
      _check_item(report, f"source.{source_path.name}", "pass", f"{source_path} exists")
    else:
      _check_item(report, f"source.{source_path.name}", "fail", f"{source_path} does not exist")

  available_scripts = (package_json or {}).get("scripts", {})
  node_modules_exists = bool(project_root and (project_root / "node_modules").exists())
  for script in browser_assets.get("scripts") or []:
    script_kind = script.get("kind", "npm")
    script_entry = {
        "id": script.get("id", ""),
        "kind": script_kind,
        "script": script.get("script", ""),
        "command": script.get("command", ""),
        "workdir": script.get("workdir", ""),
        "requiredPaths": script.get("requiredPaths", []),
        "requiresNodeModules": bool(script.get("requiresNodeModules", False)),
        "description": script.get("description", ""),
        "status": "pending",
        "reason": "",
    }
    script_name = script_entry["script"]

    missing_paths = []
    for path_value in script_entry["requiredPaths"]:
      resolved_path = _resolve_path(path_value)
      if not resolved_path.exists():
        missing_paths.append(str(resolved_path))
    if missing_paths:
      script_entry["status"] = "blocked"
      script_entry["reason"] = "required paths are missing: " + ", ".join(missing_paths)
      report["scripts"].append(script_entry)
      continue

    if script_kind == "npm":
      if not project_root:
        script_entry["status"] = "blocked"
        script_entry["reason"] = "project root is not configured"
      elif not package_json:
        script_entry["status"] = "blocked"
        script_entry["reason"] = "package.json is not available"
      elif script_name not in available_scripts:
        script_entry["status"] = "blocked"
        script_entry["reason"] = f"script {script_name} is not declared in package.json"
      elif script_entry["requiresNodeModules"] and not node_modules_exists:
        script_entry["status"] = "blocked"
        script_entry["reason"] = "node_modules is missing for a script that requires project dependencies"
    elif script_kind == "shell":
      if not script_entry["command"]:
        script_entry["status"] = "blocked"
        script_entry["reason"] = "shell command is empty"
      workdir = _resolve_path(script_entry["workdir"]) if script_entry["workdir"] else ROOT
      if script_entry["status"] != "blocked" and not workdir.exists():
        script_entry["status"] = "blocked"
        script_entry["reason"] = f"workdir does not exist: {workdir}"
      script_entry["workdir"] = str(workdir)
    else:
      script_entry["status"] = "blocked"
      script_entry["reason"] = f"unsupported script kind: {script_kind}"
    report["scripts"].append(script_entry)

  return report


def build_script_plan(report):
  return [
      {
          "script_id": script.get("id", ""),
          "script": script.get("script", ""),
          "status": script.get("status", "pending"),
          "reason": script.get("reason", ""),
          "description": script.get("description", ""),
      }
      for script in report.get("scripts", [])
  ]


def _summary(report, executions, mode):
  return {
      "mode": mode,
      "check_fail_count": sum(1 for item in report.get("checks", []) if item.get("status") == "fail"),
      "script_count": len(report.get("scripts", [])),
      "blocked_script_count": sum(1 for item in report.get("scripts", []) if item.get("status") == "blocked"),
      "execution_pass_count": sum(1 for item in executions if item.get("status") == "pass"),
      "execution_fail_count": sum(1 for item in executions if item.get("status") == "fail"),
      "execution_blocked_count": sum(1 for item in executions if item.get("status") == "blocked"),
  }


def _find_script(report, script_id):
  for script in report.get("scripts", []):
    if script.get("id") == script_id:
      return script
  return None


def execute_script(manifest, report, script_id):
  script = _find_script(report, script_id)
  if script is None:
    raise ValueError(f"unknown browser script id: {script_id}")
  if script.get("status") == "blocked":
    return {
        "script_id": script_id,
        "status": "blocked",
        "reason": script.get("reason", ""),
        "stdout": "",
        "stderr": "",
        "exit_code": None,
    }

  import os
  if script.get("kind") == "npm":
    browser_assets = manifest.get("browserAssets") or {}
    project_root = _resolve_path(browser_assets.get("projectRoot"))
    completed = subprocess.run(
        ["npm", "run", script.get("script", "")],
        cwd=str(project_root),
        capture_output=True,
        text=True,
        env=os.environ.copy(),
    )
  elif script.get("kind") == "shell":
    workdir = script.get("workdir") or str(ROOT)
    completed = subprocess.run(
        ["/bin/zsh", "-lc", script.get("command", "")],
        cwd=workdir,
        capture_output=True,
        text=True,
        env=os.environ.copy(),
    )
  else:
    return {
        "script_id": script_id,
        "status": "blocked",
        "reason": f"unsupported script kind: {script.get('kind')}",
        "stdout": "",
        "stderr": "",
        "exit_code": None,
    }
  return {
      "script_id": script_id,
      "status": "pass" if completed.returncode == 0 else "fail",
      "reason": "" if completed.returncode == 0 else f"script exited with {completed.returncode}",
      "stdout": completed.stdout,
      "stderr": completed.stderr,
      "exit_code": completed.returncode,
  }


def write_report(report_dir, payload):
  report_dir = Path(report_dir)
  report_dir.mkdir(parents=True, exist_ok=True)
  output_path = report_dir / "browser-asset-bridge.json"
  output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2))
  return output_path


def parse_args(argv=None):
  parser = argparse.ArgumentParser(description="Bridge scenario browser assets into a standard report/run flow")
  parser.add_argument("--e2e-manifest", required=True)
  parser.add_argument("--mode", choices=("plan", "run"), default="plan")
  parser.add_argument("--script-id")
  parser.add_argument("--run-label")
  parser.add_argument("--report-dir")
  return parser.parse_args(argv)


def main(argv=None):
  cli_args = parse_args(argv)
  manifest_path = Path(cli_args.e2e_manifest)
  manifest = _load_manifest(manifest_path)
  report = preflight_browser_assets(manifest)
  plan = build_script_plan(report)
  report_dir = Path(cli_args.report_dir) if cli_args.report_dir else _default_report_dir(manifest, cli_args.run_label)

  if cli_args.mode == "plan":
    payload = {
        "scenario": manifest.get("scenario", ""),
        "manifestPath": str(manifest_path),
        "preflight": report,
        "plan": plan,
        "executions": [],
        "summary": _summary(report, [], cli_args.mode),
    }
    write_report(report_dir, payload)
    print(f"browser asset plan generated: {len(plan)} scripts")
    return 0

  if not cli_args.script_id:
    raise SystemExit("--script-id is required when --mode run")

  execution = execute_script(manifest, report, cli_args.script_id)
  payload = {
      "scenario": manifest.get("scenario", ""),
      "manifestPath": str(manifest_path),
      "preflight": report,
      "plan": plan,
      "executions": [execution],
      "summary": _summary(report, [execution], cli_args.mode),
  }
  write_report(report_dir, payload)
  if execution.get("status") == "pass":
    print(f"browser asset script completed: {cli_args.script_id}")
    return 0
  print(f"browser asset script failed: {cli_args.script_id}")
  return 1


if __name__ == "__main__":
  raise SystemExit(main())
