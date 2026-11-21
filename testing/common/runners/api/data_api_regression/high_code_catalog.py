import re
from pathlib import Path


DATA_API_URL_PREFIXES = (
    "/api/sys/user",
    "/api/sys/role",
    "/api/organization",
    "/api/document-libraries",
    "/api/sys/data-dict",
    "/api/sys/configuration",
    "/api/user-setting",
    "/api/notification",
    "/api/sys/business-log",
    "/api/business-operation",
)

METHOD_ANNOTATIONS = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PatchMapping": "PATCH",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
}


def _extract_annotation_value(annotation_line):
  quoted = re.findall(r'"([^"]+)"', annotation_line)
  if quoted:
    return quoted[0]
  return ""


def _extract_request_method(annotation_line):
  method_match = re.search(r"RequestMethod\.([A-Z]+)", annotation_line)
  return method_match.group(1) if method_match else "GET"


def _join_url(base_path, method_path):
  base_path = (base_path or "").strip()
  method_path = (method_path or "").strip()

  if not method_path:
    return base_path or "/"

  if not base_path:
    return method_path

  if base_path.endswith("/") and method_path.startswith("/"):
    return base_path[:-1] + method_path

  if base_path.endswith("/") or method_path.startswith("/"):
    return base_path + method_path

  return base_path + "/" + method_path


def _is_data_api_relevant(url):
  return any(url.startswith(prefix) for prefix in DATA_API_URL_PREFIXES)


def scan_controller_file(path):
  path = Path(path)
  lines = path.read_text().splitlines()
  records = []
  annotation_buffer = []
  current_controller = None
  current_base_path = ""

  for line in lines:
    stripped = line.strip()
    if stripped.startswith("@"):
      annotation_buffer.append(stripped)
      continue

    class_match = re.search(r"class\s+([A-Za-z0-9_]+Controller)\b", stripped)
    if class_match:
      current_controller = class_match.group(1)
      request_mapping = next((item for item in annotation_buffer if item.startswith("@RequestMapping")), "")
      current_base_path = _extract_annotation_value(request_mapping)
      annotation_buffer = []
      continue

    method_match = re.search(r"\b(public|protected|private)\b.*\(", stripped)
    if method_match and current_controller:
      mapping_annotation = next(
          (
              item
              for item in annotation_buffer
              if any(item.startswith(f"@{annotation}") for annotation in tuple(METHOD_ANNOTATIONS) + ("RequestMapping",))
          ),
          "",
      )
      annotation_buffer = []
      if not mapping_annotation:
        continue

      http_method = _extract_request_method(mapping_annotation)
      for annotation, verb in METHOD_ANNOTATIONS.items():
        if mapping_annotation.startswith(f"@{annotation}"):
          http_method = verb
          break

      full_url = _join_url(current_base_path, _extract_annotation_value(mapping_annotation))
      if _is_data_api_relevant(full_url):
        records.append({
            "source_kind": "high-code",
            "controller": current_controller,
            "method": http_method,
            "url": full_url,
            "source_file": str(path),
        })
      continue

    if stripped and not stripped.startswith("//"):
      annotation_buffer = []

  return records


def scan_controller_tree(controller_paths):
  records = []
  for controller_path in controller_paths:
    records.extend(scan_controller_file(controller_path))
  return records
