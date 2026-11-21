import json
import re
from pathlib import Path


CRUD_URL_PATTERNS = {
    "search": "{base}",
    "getOne": "{base}/${id}",
    "add": "{base}",
    "update": "{base}/${id}",
    "delete": "{base}/${id}",
}

FILTER_VALUE_PLACEHOLDER_COMPANY_ID = "__COMPANY_ID__"
FILTER_SAMPLE_DATE_RANGE = ["2024-01-01", "2024-12-31"]
FILTER_SAMPLE_DATETIME_RANGE = ["2024-01-01 00:00:00", "2024-12-31 23:59:59"]
FILTER_SAMPLE_LIST = ["sample-1", "sample-2"]
WRITE_SAMPLE_TEXT = "sample-text"
WRITE_SAMPLE_UPDATED_TEXT = "sample-text-updated"
WRITE_SAMPLE_ID = "sample-id"
WRITE_SAMPLE_PHONE = "13800001234"
RESERVED_ROUTE_CONFLICTS = {
    "app": "namespace 'app' maps to /api/app/*, which is reserved by AppManagementController dev endpoints",
}
EXCLUDED_METADATA_MODELS = {
    "baseInfo.deviceManage": "project-owned script dependency issue; exclude from merge-blocking data API regression matrix",
}


def _to_kebab_case(value):
  return re.sub(r"(?<!^)(?=[A-Z])", "-", value).replace("_", "-").lower()


def _build_model_key(namespace, model_name):
  return ".".join(part for part in (namespace, model_name) if part)


def _model_key_to_path(model_key):
  parts = [part for part in str(model_key).split(".") if part]
  return "/" + "/".join(_to_kebab_case(part) for part in parts)


def _infer_identity_from_filename(metadata_file):
  stem = Path(metadata_file).name
  if stem.endswith(".json"):
    stem = stem[:-5]
  if stem.endswith(".business"):
    stem = stem[:-9]

  parts = [part for part in stem.split(".") if part]
  if not parts:
    return "", ""
  if len(parts) == 1:
    return parts[0], ""
  return ".".join(parts[:-1]), parts[-1]


def _dedupe(items):
  seen = set()
  ordered = []
  for item in items:
    if not item or item in seen:
      continue
    seen.add(item)
    ordered.append(item)
  return ordered


def _walk_json(node):
  if isinstance(node, dict):
    for key, value in node.items():
      yield key, value
      yield from _walk_json(value)
  elif isinstance(node, list):
    for value in node:
      yield from _walk_json(value)


def _contains_key(node, expected_key):
  return any(key == expected_key and value not in (None, "", [], {}) for key, value in _walk_json(node))


def _has_child_relations(payload):
  return any(
      isinstance(relation, dict) and str(relation.get("type") or "").lower() == "child"
      for relation in payload.get("relations") or []
  )


def _has_master_detail_api(api_config):
  if not isinstance(api_config, dict):
    return False

  detail_config = api_config.get("detail")
  if isinstance(detail_config, dict):
    if detail_config.get("children") not in (None, "", [], {}):
      return True
    return any(value not in (None, "", [], {}) for value in detail_config.values())

  return detail_config not in (None, "", [], {})


def _has_master_detail(payload, api_config):
  return _has_child_relations(payload) or _has_master_detail_api(api_config)


def _is_model_enabled(payload):
  return payload.get("isEnabled") is True


def _is_api_enabled(api_config):
  return isinstance(api_config, dict) and api_config.get("isEnabled") is True


def _has_data_model(payload):
  return bool(payload.get("fields"))


def _get_default_filters(payload):
  for root_key in ("views", "view"):
    filters = (((payload.get(root_key) or {}).get("index") or {}).get("defaultFilters"))
    if isinstance(filters, list):
      return filters, f"{root_key}.index.defaultFilters"
  return [], ""


def _infer_filter_param_name(filter_config):
  field_name = filter_config.get("fieldName") or filter_config.get("name")
  if not field_name and isinstance(filter_config.get("schema"), dict):
    field_name = filter_config["schema"].get("name")
  if not field_name:
    return None

  filter_operator = str(filter_config.get("filterOperator") or "EQ").upper()
  if filter_operator in ("EQ", "IN"):
    return field_name
  return f"{field_name}.{filter_operator}"


def _infer_filter_value_shape(filter_config):
  filter_type = str(filter_config.get("filterType") or "auto").lower()
  filter_operator = str(filter_config.get("filterOperator") or "EQ").upper()

  if filter_type == "input-date-range":
    return "date-range"
  if filter_type == "input-datetime-range":
    return "datetime-range"
  if filter_operator.endswith("IN"):
    return "list"
  if filter_operator == "BETWEEN":
    return "range"
  if filter_type == "input-number":
    return "number"
  return "scalar"


def _build_sample_filter_value(filter_config, value_shape):
  field_name = str(filter_config.get("fieldName") or filter_config.get("name") or "")
  filter_type = str(filter_config.get("filterType") or "auto").lower()
  normalized_field_name = field_name.lower()

  if normalized_field_name.endswith("companyid"):
    return FILTER_VALUE_PLACEHOLDER_COMPANY_ID
  if value_shape == "date-range":
    return list(FILTER_SAMPLE_DATE_RANGE)
  if value_shape == "datetime-range":
    return list(FILTER_SAMPLE_DATETIME_RANGE)
  if value_shape == "list":
    return list(FILTER_SAMPLE_LIST)
  if value_shape == "number":
    return 1
  if filter_type in ("select", "picker", "custom", "auto") and normalized_field_name.endswith("id"):
    return "sample-id"
  if normalized_field_name.endswith("date"):
    return "2024-01-01"
  if normalized_field_name.endswith("dt") or normalized_field_name.endswith("time"):
    return "2024-01-01 00:00:00"
  return "sample-text"


def _build_query_filters(payload):
  raw_filters, source_path = _get_default_filters(payload)
  query_filters = []
  for raw_filter in raw_filters:
    if not isinstance(raw_filter, dict):
      continue

    param_name = _infer_filter_param_name(raw_filter)
    if not param_name:
      continue

    filter_operator = str(raw_filter.get("filterOperator") or "EQ").upper()
    filter_type = str(raw_filter.get("filterType") or "auto").lower()
    value_shape = _infer_filter_value_shape(raw_filter)
    sample_value = _build_sample_filter_value(raw_filter, value_shape)
    query_filters.append({
        "field_name": raw_filter.get("fieldName") or raw_filter.get("name") or "",
        "param_name": param_name,
        "filter_kind": raw_filter.get("type") or "field",
        "filter_operator": filter_operator,
        "filter_type": filter_type,
        "value_shape": value_shape,
        "sample_value": sample_value,
        "source_path": source_path,
        "requires_context": sample_value == FILTER_VALUE_PLACEHOLDER_COMPANY_ID,
    })
  return query_filters


def _summarize_query_param_form(query_filters):
  return "; ".join(
      f"{query_filter['param_name']}[{query_filter['filter_operator']}/{query_filter['filter_type']}/{query_filter['value_shape']}]"
      for query_filter in query_filters
  )


def _build_sample_query(query_filters):
  return {
      query_filter["param_name"]: query_filter["sample_value"]
      for query_filter in query_filters
  }


def _normalize_default_value(value):
  if value in (None, ""):
    return None
  if isinstance(value, str):
    stripped = value.strip()
    if not stripped:
      return None
    if len(stripped) >= 2 and stripped[0] == stripped[-1] and stripped[0] in ("'", '"'):
      stripped = stripped[1:-1]
      if not stripped:
        return None
    if stripped.isdigit():
      return int(stripped)
    try:
      return float(stripped)
    except ValueError:
      return stripped
  return value


def _field_type_name(field):
  return str(field.get("type") or "").strip()


def _build_sample_field_value(field):
  field_type = _field_type_name(field).lower()
  default_value = _normalize_default_value(field.get("defaultValue"))
  if default_value is not None:
    if field_type in ("string", "单行文本", "多行文本", "text", "textarea", "单选", "select", "radio", "enum", "数据字典"):
      return str(default_value)
    return default_value

  field_name = str(field.get("name") or "")
  if field.get("isPrimaryKey"):
    if field_type in ("string", "编码主键", "文本主键"):
      return WRITE_SAMPLE_ID
    return None

  if field_type == "组织架构" and field_name.lower().endswith("companyid"):
    return FILTER_VALUE_PLACEHOLDER_COMPANY_ID
  if field_type in ("string", "单行文本", "多行文本", "text", "textarea"):
    return WRITE_SAMPLE_TEXT
  if field_type in ("手机号码", "phone", "mobile"):
    return WRITE_SAMPLE_PHONE
  if field_type in ("日期", "date"):
    return "2024-01-01"
  if field_type in ("日期时间", "datetime", "time", "时间"):
    return "2024-01-01 00:00:00"
  if field_type in ("整数数字", "integer", "int", "long"):
    return 1
  if field_type in ("定点数字", "decimal", "double", "float", "number"):
    return 1.23
  if field_type in ("布尔", "boolean"):
    return True
  return None


def _view_fields(payload, view_name):
  view = ((payload.get("views") or {}).get(view_name) or {})
  fields = view.get("fields")
  if isinstance(fields, list):
    return fields
  return []


def _field_map(payload):
  return {
      field.get("name"): field
      for field in payload.get("fields") or []
      if isinstance(field, dict) and field.get("name")
  }


def _relation_map(payload):
  return {
      relation.get("name"): relation
      for relation in payload.get("relations") or []
      if isinstance(relation, dict) and relation.get("name")
  }


def _write_field_signature(field_name, field):
  return f"{field_name}[{_field_type_name(field)}]"


def _stage_two_reason(reason):
  return f"stage-two-only: {reason}"


def _runtime_dependency_signature(dependency):
  if dependency.get("dependency_kind") == "reference":
    return f"{dependency['field_name']}[reference/{dependency.get('selector_type') or 'auto'}/runtime]"
  if dependency.get("dependency_kind") == "data-dict":
    return f"{dependency['field_name']}[数据字典/{dependency.get('selector_type') or 'auto'}/runtime]"
  return f"{dependency['field_name']}[runtime]"


def _build_runtime_write_dependency_for_field(field_name, field):
  field_type = _field_type_name(field).lower()
  selector_type = str(field.get("selectorType") or "").lower()
  dict_name = field.get("dictName")
  if field_type == "数据字典" and selector_type == "select" and dict_name:
    return {
        "field_name": field_name,
        "dependency_kind": "data-dict",
        "dict_name": dict_name,
        "selector_type": selector_type,
    }
  return None


def _build_runtime_write_dependency_for_relation(field_name, relation):
  relation_type = str(relation.get("type") or "").lower()
  selector_type = str(relation.get("selectorType") or "").lower()
  model_key = relation.get("model")
  reference_key = relation.get("referenceKey") or relation.get("rawFieldName") or field_name
  if relation_type == "reference" and selector_type == "select" and model_key:
    return {
        "field_name": field_name,
        "dependency_kind": "reference",
        "model_key": model_key,
        "reference_key": reference_key,
        "selector_type": selector_type,
    }
  return None


def _build_updated_field_value(field, sample_value):
  field_type = _field_type_name(field).lower()
  if sample_value == FILTER_VALUE_PLACEHOLDER_COMPANY_ID:
    return sample_value
  if field_type in ("日期", "date"):
    return "2024-01-02"
  if field_type in ("日期时间", "datetime", "time", "时间"):
    return "2024-01-02 00:00:00"
  if field_type in ("整数数字", "integer", "int", "long"):
    return 2
  if field_type in ("定点数字", "decimal", "double", "float", "number"):
    return 2.34
  if field_type in ("布尔", "boolean"):
    return False
  if isinstance(sample_value, str):
    return WRITE_SAMPLE_UPDATED_TEXT
  return sample_value


def _append_payload_field(payload_form, sample_payload, field_name, field, sample_value):
  if field_name not in sample_payload:
    payload_form.append(_write_field_signature(field_name, field))
    sample_payload[field_name] = sample_value


def _preserve_hidden_required_field(field_name, field, payload_form, sample_payload):
  if field.get("isPrimaryKey"):
    return True

  sample_value = _build_sample_field_value(field)
  if sample_value is not None:
    _append_payload_field(payload_form, sample_payload, field_name, field, sample_value)
    return True

  return False


def _build_write_sample_metadata(payload):
  fields_by_name = _field_map(payload)
  relations_by_name = _relation_map(payload)
  explicit_add_view_fields = _view_fields(payload, "add")
  add_view_fields = list(explicit_add_view_fields)
  edit_view_fields = _view_fields(payload, "edit")
  if not add_view_fields:
    add_view_fields = [
        {
            "name": field_name,
            "type": "field",
        }
        for field_name in fields_by_name.keys()
        if fields_by_name.get(field_name, {}).get("isPrimaryKey") or fields_by_name.get(field_name, {}).get("isRequired")
    ]
    add_view_fields.extend(
        {
            "name": relation_name,
            "type": "relation",
        }
        for relation_name, relation in relations_by_name.items()
        if relation.get("isRequired")
    )

  payload_form = []
  sample_payload = {}
  primary_key_field = ""
  skipped_reasons = []
  blocking_reasons = []
  runtime_write_dependencies = []
  for view_field in add_view_fields:
    if not isinstance(view_field, dict):
      skipped_reasons.append("unsupported add view field metadata")
      continue

    field_name = view_field.get("name") or view_field.get("field")
    if not field_name:
      skipped_reasons.append("missing field name in add view metadata")
      continue

    view_field_type = view_field.get("type") or "field"
    if view_field_type == "field":
      field = fields_by_name.get(field_name)
      if field:
        sample_value = _build_sample_field_value(field)
        should_emit_field = bool(field.get("isPrimaryKey") or field.get("isRequired") or _normalize_default_value(field.get("defaultValue")) is not None)
        if sample_value is not None and should_emit_field:
          payload_form.append(_write_field_signature(field_name, field))
          sample_payload[field_name] = sample_value
          if field.get("isPrimaryKey"):
            primary_key_field = field_name
          continue
        if not should_emit_field:
          skipped_reasons.append(f"optional field {field_name} omitted from minimal add payload")
          continue

        runtime_dependency = _build_runtime_write_dependency_for_field(field_name, field)
        if runtime_dependency:
          payload_form.append(_runtime_dependency_signature(runtime_dependency))
          runtime_write_dependencies.append(runtime_dependency)
          continue

        reason = f"unsupported field type {_field_type_name(field)} for {field_name}"
        if field.get("isRequired"):
          blocking_reasons.append(reason)
        else:
          skipped_reasons.append(reason)
        continue

      relation = relations_by_name.get(field_name)
      if relation:
        runtime_dependency = _build_runtime_write_dependency_for_relation(field_name, relation)
        if runtime_dependency:
          payload_form.append(_runtime_dependency_signature(runtime_dependency))
          runtime_write_dependencies.append(runtime_dependency)
          continue

        reason = (
            f"unsupported relation {relation.get('type') or 'unknown'}/"
            f"{relation.get('selectorType') or 'unknown'} for {field_name}"
        )
        if relation.get("isRequired"):
          blocking_reasons.append(reason)
        else:
          skipped_reasons.append(reason)
        continue

      skipped_reasons.append(f"field {field_name} not found in model fields")
      continue

    if view_field_type == "relation":
      relation = relations_by_name.get(field_name)
      if not relation:
        skipped_reasons.append(f"relation {field_name} not found in model relations")
        continue

      runtime_dependency = _build_runtime_write_dependency_for_relation(field_name, relation)
      if runtime_dependency:
        payload_form.append(_runtime_dependency_signature(runtime_dependency))
        runtime_write_dependencies.append(runtime_dependency)
        continue

      reason = (
          f"unsupported relation {relation.get('type') or 'unknown'}/"
          f"{relation.get('selectorType') or 'unknown'} for {field_name}"
      )
      if relation.get("isRequired"):
        blocking_reasons.append(reason)
      else:
        skipped_reasons.append(reason)
      continue

    skipped_reasons.append(f"unsupported add view field type {view_field_type}")

  if not primary_key_field:
    for field_name, field in fields_by_name.items():
      if not field.get("isPrimaryKey"):
        continue
      sample_value = _build_sample_field_value(field)
      if sample_value is None:
        skipped_reasons.append(f"unsupported field type {_field_type_name(field)} for {field_name}")
        break
      primary_key_field = field_name
      sample_payload[field_name] = sample_value
      payload_form.insert(0, _write_field_signature(field_name, field))
      break

  if explicit_add_view_fields:
    declared_add_view_entries = {
        view_field.get("name") or view_field.get("field")
        for view_field in explicit_add_view_fields
        if isinstance(view_field, dict) and (view_field.get("name") or view_field.get("field"))
    }

    for field_name, field in fields_by_name.items():
      if not field.get("isRequired"):
        continue
      if field_name in declared_add_view_entries:
        continue
      if _preserve_hidden_required_field(field_name, field, payload_form, sample_payload):
        continue
      blocking_reasons.append(f"required field {field_name} omitted from add view")

    for relation_name, relation in relations_by_name.items():
      if not relation.get("isRequired"):
        continue
      if relation_name in declared_add_view_entries:
        continue
      blocking_reasons.append(f"required relation {relation_name} omitted from add view")

  if blocking_reasons:
    reason = _stage_two_reason("; ".join(sorted(set(blocking_reasons))))
    return {
        "payload_form": "; ".join(payload_form),
        "sample_payload": sample_payload,
        "sample_update_payload": {},
        "runtime_write_dependencies": runtime_write_dependencies,
        "write_primary_key_field": primary_key_field,
        "create_executable": False,
        "create_reason": reason,
        "update_executable": False,
        "update_reason": reason,
        "delete_executable": False,
        "delete_reason": reason,
    }

  if not sample_payload and not runtime_write_dependencies:
    reason = _stage_two_reason("no supported fields remained after write payload filtering")
    return {
        "payload_form": "",
        "sample_payload": {},
        "sample_update_payload": {},
        "runtime_write_dependencies": [],
        "write_primary_key_field": "",
        "create_executable": False,
        "create_reason": reason,
        "update_executable": False,
        "update_reason": reason,
        "delete_executable": False,
        "delete_reason": reason,
    }

  if not primary_key_field:
    reason = _stage_two_reason("missing explicit primary key field in add view")
    return {
        "payload_form": "; ".join(payload_form),
        "sample_payload": sample_payload,
        "sample_update_payload": {},
        "runtime_write_dependencies": runtime_write_dependencies,
        "write_primary_key_field": "",
        "create_executable": False,
        "create_reason": reason,
        "update_executable": False,
        "update_reason": reason,
        "delete_executable": False,
        "delete_reason": reason,
    }

  sample_update_payload = {}
  update_reason = ""
  for view_field in edit_view_fields:
    if not isinstance(view_field, dict) or view_field.get("type") != "field":
      continue
    field_name = view_field.get("name") or view_field.get("field")
    if field_name == primary_key_field:
      continue
    field = fields_by_name.get(field_name)
    if not field:
      continue
    sample_value = _build_sample_field_value(field)
    if isinstance(sample_value, str):
      sample_update_payload[field_name] = _build_updated_field_value(field, sample_value)
      break
    if sample_value is not None:
      sample_update_payload[field_name] = _build_updated_field_value(field, sample_value)
      break

  if not sample_update_payload:
    for field_name, field in fields_by_name.items():
      if field.get("isPrimaryKey"):
        continue
      sample_value = _build_sample_field_value(field)
      if sample_value is None:
        continue
      sample_update_payload[field_name] = _build_updated_field_value(field, sample_value)
      break

  if not sample_update_payload:
    update_reason = _stage_two_reason("missing editable field sample for update ring")

  reason_parts = []
  if runtime_write_dependencies:
    reason_parts.append(
        "runtime-dependencies: " + "; ".join(_runtime_dependency_signature(dependency) for dependency in runtime_write_dependencies)
    )
  if skipped_reasons:
    reason_parts.append("skipped-fields: " + "; ".join(sorted(set(skipped_reasons))))
  create_reason = "; ".join(reason_parts)
  delete_reason = create_reason
  if create_reason:
    if update_reason:
      update_reason = f"{update_reason}; {create_reason}"
    else:
      update_reason = create_reason
  return {
      "payload_form": "; ".join(payload_form),
      "sample_payload": sample_payload,
      "sample_update_payload": sample_update_payload,
      "runtime_write_dependencies": runtime_write_dependencies,
      "write_primary_key_field": primary_key_field,
      "create_executable": True,
      "create_reason": create_reason,
      "update_executable": bool(sample_update_payload),
      "update_reason": update_reason,
      "delete_executable": True,
      "delete_reason": delete_reason,
  }


def _detect_reserved_route_conflict(namespace, api_name, inferred_urls):
  if api_name not in ("search", "add"):
    return False, ""

  reason = RESERVED_ROUTE_CONFLICTS.get(namespace)
  if not reason:
    return False, ""

  if any(url.startswith(f"/api/{namespace}/") for url in inferred_urls):
    return True, reason
  return False, ""


def _normalize_api_value(value):
  if isinstance(value, dict):
    url = value.get("url")
    if isinstance(url, str):
      return _normalize_api_value(url)
    return None

  if not isinstance(value, str):
    return None

  candidate = value.strip()
  if not candidate:
    return None

  if ":/api/" in candidate:
    _, _, candidate = candidate.partition(":")

  if candidate.startswith("api/"):
    candidate = "/" + candidate

  if candidate.startswith("/api/"):
    return candidate

  return None


def extract_ui_schema_references_from_file(path):
  schema = json.loads(Path(path).read_text())
  references = []
  for key, value in _walk_json(schema):
    if key == "schemaId" and isinstance(value, str):
      references.append({"kind": "schemaId", "value": value})
      continue

    if key == "source":
      normalized = _normalize_api_value(value)
      if normalized:
        references.append({"kind": "source", "value": normalized})
      continue

    if key == "crud":
      normalized = _normalize_api_value(value)
      if normalized:
        references.append({"kind": "crud", "value": normalized})
      continue

    if key == "api":
      normalized = _normalize_api_value(value)
      if normalized:
        references.append({"kind": "api", "value": normalized})
      continue

  unique_pairs = _dedupe([(reference["kind"], reference["value"]) for reference in references])
  return [{"kind": kind, "value": value} for kind, value in unique_pairs]


def collect_ui_schema_references(ui_schema_dir):
  ui_schema_dir = Path(ui_schema_dir)
  if not ui_schema_dir.exists():
    return {}

  references_by_file = {}
  for schema_file in sorted(ui_schema_dir.glob("*.json")):
    references_by_file[schema_file.name] = extract_ui_schema_references_from_file(schema_file)
  return references_by_file


def _prepare_ui_schema_references(ui_schema_files_or_references):
  prepared = []
  for item in ui_schema_files_or_references or []:
    if isinstance(item, (str, Path)):
      prepared.append(extract_ui_schema_references_from_file(item))
    else:
      prepared.append(item)
  return prepared


def _infer_model_urls(model_key, api_name):
  base = f"/api{_model_key_to_path(model_key)}"
  pattern = CRUD_URL_PATTERNS.get(api_name, "{base}/" + _to_kebab_case(api_name))
  return [pattern.replace("{base}", base)]


def _infer_schema_prefix(model_key):
  return _model_key_to_path(model_key)


def _filter_matching_references(model_key, api_name, ui_schema_references):
  base_url = _infer_model_urls(model_key, api_name)[0].replace("/${id}", "")
  schema_prefix = _infer_schema_prefix(model_key)

  matched_urls = []
  matched_schema_ids = []
  for references in ui_schema_references:
    for reference in references:
      value = reference["value"]
      if reference["kind"] == "schemaId" and value.startswith(schema_prefix):
        matched_schema_ids.append(value)
      elif reference["kind"] != "schemaId" and value.startswith(base_url):
        matched_urls.append(value)

  return _dedupe(matched_urls), _dedupe(matched_schema_ids)


def build_metadata_inventory(metadata_file, ui_schema_files=None):
  metadata_file = Path(metadata_file)
  payload = json.loads(metadata_file.read_text())
  if not isinstance(payload, dict):
    return []
  if not _is_model_enabled(payload):
    return []
  if not _has_data_model(payload):
    return []

  fallback_namespace, fallback_model_name = _infer_identity_from_filename(metadata_file)
  namespace = payload.get("namespace") or fallback_namespace
  model_name = payload.get("name") or fallback_model_name
  model_key = _build_model_key(namespace, model_name)
  if model_key in EXCLUDED_METADATA_MODELS:
    return []
  apis = payload.get("apis") or {}
  ui_schema_references = _prepare_ui_schema_references(ui_schema_files)
  write_sample_metadata = _build_write_sample_metadata(payload)

  records = []
  for api_name, api_config in apis.items():
    api_config = api_config or {}
    if not _is_api_enabled(api_config):
      continue
    inferred_urls = _infer_model_urls(model_key, api_name)
    matched_urls, matched_schema_ids = _filter_matching_references(model_key, api_name, ui_schema_references)
    query_filters = _build_query_filters(payload) if api_name == "search" else []
    reserved_route_conflict, reserved_route_reason = _detect_reserved_route_conflict(namespace, api_name, inferred_urls)
    records.append({
        "source_kind": "metadata",
        "model_key": model_key,
        "model_title": payload.get("title", model_key),
        "api_name": api_name,
        "urls": _dedupe(inferred_urls + matched_urls),
        "schema_ids": matched_schema_ids,
        "source_file": str(metadata_file),
        "has_before_script": bool(api_config.get("beforeScript")),
        "has_after_script": bool(api_config.get("afterScript")),
        "has_detail": _has_master_detail(payload, api_config),
        "populate_count": len(api_config.get("populate") or []),
        "query_filter_count": len(query_filters),
        "query_filters": query_filters,
        "query_param_form": _summarize_query_param_form(query_filters),
        "sample_query": _build_sample_query(query_filters),
        "detail_primary_key_field": write_sample_metadata["write_primary_key_field"] if api_name == "getOne" else "",
        "payload_form": write_sample_metadata["payload_form"] if api_name in ("add", "update", "delete") else "",
        "sample_payload": dict(write_sample_metadata["sample_payload"]) if api_name in ("add", "update", "delete") else {},
        "sample_update_payload": dict(write_sample_metadata["sample_update_payload"]) if api_name == "update" else {},
        "runtime_write_dependencies": [
            dict(dependency)
            for dependency in write_sample_metadata.get("runtime_write_dependencies", [])
        ] if api_name in ("add", "update", "delete") else [],
        "write_primary_key_field": write_sample_metadata["write_primary_key_field"] if api_name in ("add", "update", "delete") else "",
        "stage_one_write_executable": (
            write_sample_metadata["create_executable"] if api_name == "add"
            else write_sample_metadata["update_executable"] if api_name == "update"
            else write_sample_metadata["delete_executable"] if api_name == "delete"
            else False
        ),
        "stage_one_write_reason": (
            write_sample_metadata["create_reason"] if api_name == "add"
            else write_sample_metadata["update_reason"] if api_name == "update"
            else write_sample_metadata["delete_reason"] if api_name == "delete"
            else ""
        ),
        "reserved_route_conflict": reserved_route_conflict,
        "reserved_route_reason": reserved_route_reason,
    })
  return records


def collect_metadata_inventory(business_model_dir, ui_schema_dir=None):
  business_model_dir = Path(business_model_dir)
  ui_schema_dir = Path(ui_schema_dir) if ui_schema_dir else None
  ui_schema_files = []
  if ui_schema_dir and ui_schema_dir.exists():
    ui_schema_files = list(collect_ui_schema_references(ui_schema_dir).values())

  records = []
  for metadata_file in sorted(business_model_dir.glob("*.json")):
    records.extend(build_metadata_inventory(metadata_file, ui_schema_files))
  return records
