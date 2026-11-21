#!/usr/bin/env bash
set -euo pipefail

assembly_pom="ability/ouroboros-ability-all/ouroboros-ability-all-runtime/pom.xml"
stable_starter_pom="ability/ouroboros-ai/ouroboros-ai-starter/pom.xml"
spring_ai_starter_pom="ability/ouroboros-ai/ouroboros-ai-starter-spring-ai/pom.xml"
output_dir="target/ai-runtime-hygiene"
artifact_glob="platform/ouroboros-mothership/docker/build-layer/app/base/service/ouroboros-mothership.jar"
package_lib_dirs=()
mode="static"

die() {
  echo "$*" >&2
  exit 1
}

usage() {
  cat <<USAGE
Usage: bash scripts/ci/verify-ai-runtime-hygiene.sh [--static|--resolved|--package|--all] [--output-dir DIR] [--artifact GLOB] [--package-lib-dir DIR]

Modes:
  --static    Check declared POM wiring only (default, Jenkins-safe).
  --resolved  Generate and inspect runtime dependency trees.
  --package   Inspect the packaged mothership runtime dependency layer.
  --all       Run static, resolved, then package checks.

Package mode reads release layer directories. Pass --package-lib-dir one or more
times to point at an equivalent package mirror. If omitted, it reads the
mothership Docker ouroboros-lib and third-party-lib directories.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --static)
      mode="static"
      shift
      ;;
    --resolved)
      mode="resolved"
      shift
      ;;
    --package)
      mode="package"
      shift
      ;;
    --all)
      mode="all"
      shift
      ;;
    --output-dir)
      [ "$#" -ge 2 ] || die "Missing value for --output-dir"
      output_dir="$2"
      shift 2
      ;;
    --artifact)
      [ "$#" -ge 2 ] || die "Missing value for --artifact"
      artifact_glob="$2"
      shift 2
      ;;
    --package-lib-dir)
      [ "$#" -ge 2 ] || die "Missing value for --package-lib-dir"
      package_lib_dirs+=("$2")
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown argument: $1"
      ;;
  esac
done

require_file() {
  local path="$1"

  if [ ! -f "$path" ]; then
    die "Required file not found: $path"
  fi
}

has_artifact() {
  local artifact_id="$1"
  local path="$2"

  grep -Eq "<artifactId>[[:space:]]*${artifact_id}[[:space:]]*</artifactId>" "$path"
}

print_matches() {
  local artifact_pattern="$1"
  local path="$2"

  grep -En "<artifactId>[[:space:]]*${artifact_pattern}[[:space:]]*</artifactId>" "$path" || true
}

has_artifact_pattern() {
  local artifact_pattern="$1"
  local path="$2"

  grep -Eq "<artifactId>[[:space:]]*${artifact_pattern}[[:space:]]*</artifactId>" "$path"
}

assert_no_match_file() {
  local pattern="$1"
  local path="$2"
  local message="$3"

  if grep -En "$pattern" "$path"; then
    die "$message: $path"
  fi
}

assert_match_file() {
  local pattern="$1"
  local path="$2"
  local message="$3"

  grep -Eq "$pattern" "$path" || die "$message: $path"
}

has_match_file() {
  local pattern="$1"
  local path="$2"

  grep -Eq "$pattern" "$path"
}

run_static() {
  require_file "$assembly_pom"
  require_file "$stable_starter_pom"

  # Keep this check static and deterministic. Running Maven dependency:tree here made
  # the release gate depend on Jenkins Maven/toolchain state before packaging, while
  # the policy only needs to validate the declared assembly POM wiring.
  if has_artifact "ouroboros-ai-starter-spring-ai" "$assembly_pom"; then
    if has_artifact "ouroboros-ai-starter" "$assembly_pom" || has_artifact "ouroboros-ai-starter-legacy" "$assembly_pom"; then
      echo "Mixed AI starter families detected in $assembly_pom"
      print_matches "ouroboros-ai-starter(-legacy|-spring-ai)?" "$assembly_pom"
      exit 1
    fi
  fi

  if has_artifact "ouroboros-ai-starter-legacy" "$stable_starter_pom" \
    && has_artifact "ouroboros-ai-starter-spring-ai" "$stable_starter_pom"; then
    echo "Stable AI starter bridges to multiple starter families in $stable_starter_pom"
    print_matches "ouroboros-ai-starter(-legacy|-spring-ai)?" "$stable_starter_pom"
    exit 1
  fi

  if has_artifact_pattern "ouroboros-ai-(runtime|openai-runtime|anthropic-runtime|rag-runtime|mcp-runtime)" "$assembly_pom"; then
    print_matches "ouroboros-ai-(runtime|openai-runtime|anthropic-runtime|rag-runtime|mcp-runtime)" "$assembly_pom"
    echo "Direct AI provider/runtime assembly detected in ability-all runtime"
    exit 1
  fi
}

write_dependency_tree() {
  local module="$1"
  local output_file="$2"
  local output_dirname
  local output_basename
  local output_absolute

  output_dirname="$(dirname "$output_file")"
  output_basename="$(basename "$output_file")"
  mkdir -p "$output_dirname"
  output_absolute="$(cd "$output_dirname" && pwd)/$output_basename"
  mvn -q -pl ":${module}" -DskipTests -Djacoco.skip=true dependency:tree \
    -Dscope=runtime \
    -Dincludes="com.ouroboros:ouroboros-ai-*,org.springframework.ai:*,dev.langchain4j:*" \
    -DoutputFile="$output_absolute"
  require_file "$output_file"
}

run_resolved() {
  local ability_tree="$output_dir/ability-all-ai-runtime-dependency-tree.txt"
  local openai_tree="$output_dir/openai-starter-ai-runtime-dependency-tree.txt"
  local anthropic_tree="$output_dir/anthropic-starter-ai-runtime-dependency-tree.txt"
  local spring_ai_tree="$output_dir/spring-ai-starter-ai-runtime-dependency-tree.txt"

  write_dependency_tree "ouroboros-ability-all-runtime" "$ability_tree"
  assert_no_match_file "com\\.ouroboros:ouroboros-ai-(starter-legacy|starter-openai|starter-anthropic|starter-spring-ai|openai-runtime|anthropic-runtime|rag-runtime|mcp-runtime):" \
    "$ability_tree" "ability-all runtime dependency tree contains provider-specific or non-default AI runtime artifact"
  assert_no_match_file "org\\.springframework\\.ai:" "$ability_tree" \
    "ability-all runtime dependency tree contains Spring AI provider artifact"
  assert_no_match_file "dev\\.langchain4j:" "$ability_tree" \
    "ability-all runtime dependency tree contains hidden LangChain4j dependency"

  write_dependency_tree "ouroboros-ai-starter-openai" "$openai_tree"
  assert_match_file "com\\.ouroboros:ouroboros-ai-openai-runtime:" "$openai_tree" \
    "OpenAI starter dependency tree does not include OpenAI provider runtime"
  assert_no_match_file "com\\.ouroboros:ouroboros-ai-(starter-legacy|starter-anthropic|starter-spring-ai|anthropic-runtime|rag-runtime|mcp-runtime):" \
    "$openai_tree" "OpenAI starter dependency tree crosses provider or starter family boundary"
  assert_no_match_file "dev\\.langchain4j:" "$openai_tree" \
    "OpenAI starter dependency tree contains hidden LangChain4j dependency"

  write_dependency_tree "ouroboros-ai-starter-anthropic" "$anthropic_tree"
  assert_match_file "com\\.ouroboros:ouroboros-ai-anthropic-runtime:" "$anthropic_tree" \
    "Anthropic starter dependency tree does not include Anthropic provider runtime"
  assert_no_match_file "com\\.ouroboros:ouroboros-ai-(starter-legacy|starter-openai|starter-spring-ai|openai-runtime|rag-runtime|mcp-runtime):" \
    "$anthropic_tree" "Anthropic starter dependency tree crosses provider or starter family boundary"
  assert_no_match_file "dev\\.langchain4j:" "$anthropic_tree" \
    "Anthropic starter dependency tree contains hidden LangChain4j dependency"

  require_file "$spring_ai_starter_pom"
  if has_artifact_pattern "ouroboros-ai-(openai-runtime|anthropic-runtime|rag-runtime|mcp-runtime)" "$spring_ai_starter_pom"; then
    print_matches "ouroboros-ai-(openai-runtime|anthropic-runtime|rag-runtime|mcp-runtime)" "$spring_ai_starter_pom"
    die "Spring AI aggregate starter must depend on provider starters, not runtime modules directly"
  fi
  write_dependency_tree "ouroboros-ai-starter-spring-ai" "$spring_ai_tree"
  assert_no_match_file "com\\.ouroboros:ouroboros-ai-starter-legacy:" "$spring_ai_tree" \
    "Spring AI aggregate starter dependency tree contains legacy starter family"
  assert_no_match_file "dev\\.langchain4j:" "$spring_ai_tree" \
    "Spring AI aggregate starter dependency tree contains hidden LangChain4j dependency"
}

resolve_artifact() {
  local matches=()
  local candidates=()
  local match
  while IFS= read -r match; do
    matches+=("$match")
  done < <(compgen -G "$artifact_glob" | sort)

  if [ "${#matches[@]}" -eq 0 ]; then
    die "No package artifact matched: $artifact_glob"
  fi
  for match in "${matches[@]}"; do
    case "$match" in
      *-sources.jar|*-javadoc.jar|*-tests.jar)
        ;;
      *)
        candidates+=("$match")
        ;;
    esac
  done
  if [ "${#candidates[@]}" -eq 0 ]; then
    printf '%s\n' "${matches[@]}"
    die "No package artifact candidate matched: $artifact_glob"
  fi
  if [ "${#candidates[@]}" -gt 1 ]; then
    printf '%s\n' "${candidates[@]}"
    die "Package artifact glob matched multiple files: $artifact_glob"
  fi
  printf '%s\n' "${candidates[0]}"
}

absolute_path() {
  local path="$1"
  local dirname
  local basename

  dirname="$(dirname "$path")"
  basename="$(basename "$path")"
  [ -d "$dirname" ] || die "Parent directory not found: $dirname"
  printf '%s/%s\n' "$(cd "$dirname" && pwd -P)" "$basename"
}

assert_package_artifact_source() {
  local artifact="$1"
  local artifact_absolute
  local expected_artifact
  local runtime_lib_dir
  local candidate_artifact

  artifact_absolute="$(absolute_path "$artifact")"

  if [ "${#package_lib_dirs[@]}" -eq 0 ]; then
    expected_artifact="$(absolute_path "platform/ouroboros-mothership/docker/build-layer/app/base/service/ouroboros-mothership.jar")"
    [ "$artifact_absolute" = "$expected_artifact" ] || die \
      "Package artifact must come from the official mothership package layer: expected $expected_artifact, got $artifact_absolute"
    return
  fi

  for runtime_lib_dir in "${package_lib_dirs[@]}"; do
    [ -d "$runtime_lib_dir" ] || die "Package runtime lib directory not found: $runtime_lib_dir"
    candidate_artifact="$(absolute_path "$(dirname "$runtime_lib_dir")/ouroboros-mothership.jar")"
    [ "$artifact_absolute" = "$candidate_artifact" ] || die \
      "Package artifact must be the ouroboros-mothership.jar colocated with every supplied package lib dir: expected $candidate_artifact, got $artifact_absolute"
  done
}

write_runtime_lib_listing() {
  local output_file="$1"
  local output_dirname
  local output_basename
  local output_absolute
  local dirs=("${package_lib_dirs[@]}")
  local runtime_lib_dir

  output_dirname="$(dirname "$output_file")"
  output_basename="$(basename "$output_file")"
  mkdir -p "$output_dirname"
  output_absolute="$(cd "$output_dirname" && pwd)/$output_basename"

  if [ "${#dirs[@]}" -eq 0 ]; then
    dirs=(
      "platform/ouroboros-mothership/docker/build-layer/app/base/service/ouroboros-lib"
      "platform/ouroboros-mothership/docker/common-layer/app/base/service/third-party-lib"
    )
  fi

  : > "$output_absolute"
  for runtime_lib_dir in "${dirs[@]}"; do
    [ -d "$runtime_lib_dir" ] || die "Package runtime lib directory not found: $runtime_lib_dir"
    find "$runtime_lib_dir" -maxdepth 1 -type f -name '*.jar' -exec basename {} \; >> "$output_absolute"
  done
  sort -u "$output_absolute" -o "$output_absolute"
  [ -s "$output_file" ] || die "Package runtime lib directory has no jar files: $runtime_lib_dir"
}

runtime_lib_path() {
  local jar_name="$1"
  local dirs=("${package_lib_dirs[@]}")
  local runtime_lib_dir

  if [ "${#dirs[@]}" -eq 0 ]; then
    dirs=(
      "platform/ouroboros-mothership/docker/build-layer/app/base/service/ouroboros-lib"
      "platform/ouroboros-mothership/docker/common-layer/app/base/service/third-party-lib"
    )
  fi
  for runtime_lib_dir in "${dirs[@]}"; do
    if [ -f "$runtime_lib_dir/$jar_name" ]; then
      printf '%s/%s\n' "$runtime_lib_dir" "$jar_name"
      return 0
    fi
  done
  return 1
}

assert_mothership_artifact() {
  local artifact="$1"

  if ! unzip -p "$artifact" META-INF/MANIFEST.MF \
      | grep -Eq '^Start-Class:[[:space:]]+com\.ouroboros\.mothership\.OuroborosApplication[[:space:]]*$'; then
    die "Package artifact is not a mothership application jar: $artifact"
  fi
}

assert_selected_provider_family() {
  local libs_file="$1"

  assert_match_file '^ouroboros-ai-core-.*\.jar$' "$libs_file" \
    "AI package is missing ouroboros-ai-core"
  assert_match_file '^ouroboros-ai-runtime-.*\.jar$' "$libs_file" \
    "AI package is missing ouroboros-ai-runtime"
  assert_match_file '^ouroboros-ai-starter-runtime-.*\.jar$' "$libs_file" \
    "AI package is missing ouroboros-ai-starter-runtime"

  if has_match_file '^ouroboros-ai-starter-spring-ai-.*\.jar$' "$libs_file"; then
    assert_match_file '^ouroboros-ai-starter-openai-.*\.jar$' "$libs_file" \
      "Spring AI aggregate starter package is missing OpenAI provider starter"
    assert_match_file '^ouroboros-ai-starter-anthropic-.*\.jar$' "$libs_file" \
      "Spring AI aggregate starter package is missing Anthropic provider starter"
    assert_match_file '^ouroboros-ai-openai-runtime-.*\.jar$' "$libs_file" \
      "Spring AI aggregate starter package is missing OpenAI runtime"
    assert_match_file '^ouroboros-ai-anthropic-runtime-.*\.jar$' "$libs_file" \
      "Spring AI aggregate starter package is missing Anthropic runtime"
    assert_match_file '^spring-ai-openai-.*\.jar$' "$libs_file" \
      "Spring AI aggregate starter package is missing spring-ai-openai"
    assert_match_file '^spring-ai-anthropic-.*\.jar$' "$libs_file" \
      "Spring AI aggregate starter package is missing spring-ai-anthropic"
    assert_match_file '^openai-java-core-.*\.jar$' "$libs_file" \
      "Spring AI aggregate starter package is missing OpenAI SDK runtime"
    assert_match_file '^anthropic-java-core-.*\.jar$' "$libs_file" \
      "Spring AI aggregate starter package is missing Anthropic SDK runtime"
    return
  fi

  if has_match_file '^ouroboros-ai-starter-openai-.*\.jar$' "$libs_file"; then
    assert_match_file '^ouroboros-ai-openai-runtime-.*\.jar$' "$libs_file" \
      "OpenAI starter package is missing OpenAI runtime"
    assert_match_file '^spring-ai-openai-.*\.jar$' "$libs_file" \
      "OpenAI starter package is missing spring-ai-openai"
    assert_match_file '^openai-java-core-.*\.jar$' "$libs_file" \
      "OpenAI starter package is missing OpenAI SDK runtime"
    return
  fi

  if has_match_file '^ouroboros-ai-starter-anthropic-.*\.jar$' "$libs_file"; then
    assert_match_file '^ouroboros-ai-anthropic-runtime-.*\.jar$' "$libs_file" \
      "Anthropic starter package is missing Anthropic runtime"
    assert_match_file '^spring-ai-anthropic-.*\.jar$' "$libs_file" \
      "Anthropic starter package is missing spring-ai-anthropic"
    assert_match_file '^anthropic-java-core-.*\.jar$' "$libs_file" \
      "Anthropic starter package is missing Anthropic SDK runtime"
    return
  fi

  die "Package runtime layer does not include a selected AI provider starter"
}

run_package() {
  local artifact
  local libs_file="$output_dir/mothership-ai-runtime-libs.txt"
  local boot_libs_file="$output_dir/mothership-boot-jar-libs.txt"
  local boot_entries_file="$output_dir/mothership-boot-jar-entries.txt"
  local auto_config_file="$output_dir/mothership-ai-autoconfiguration-resources.txt"
  local ai_lib_count

  mkdir -p "$output_dir"
  artifact="$(resolve_artifact)"
  assert_package_artifact_source "$artifact"
  jar tf "$artifact" > "$boot_entries_file" || die "Package artifact is not a readable jar: $artifact"
  assert_mothership_artifact "$artifact"
  sed -n 's#^BOOT-INF/lib/##p' "$boot_entries_file" | grep -E '(^|/)?.*\.jar$' | sort > "$boot_libs_file" || true
  write_runtime_lib_listing "$libs_file"

  assert_no_match_file "dev\\.langchain4j|langchain4j" "$libs_file" \
    "Package runtime layer contains hidden LangChain4j dependency"
  assert_no_match_file '^ouroboros-ai-starter-legacy-.*\.jar$' "$libs_file" \
    "Package runtime layer contains legacy AI starter after Spring AI default switch"
  assert_selected_provider_family "$libs_file"

  if grep -Eq 'ouroboros-ai-starter-legacy' "$libs_file" \
    && grep -Eq 'ouroboros-ai-starter-spring-ai' "$libs_file"; then
    grep -En 'ouroboros-ai-starter-(legacy|spring-ai)' "$libs_file" || true
    die "Package runtime layer contains mixed legacy and Spring AI starter families"
  fi

  : > "$auto_config_file"
  while IFS= read -r nested; do
    case "$nested" in
      *ouroboros-ai-*.jar|*spring-ai-*.jar)
        local nested_jar
        nested_jar="$(runtime_lib_path "$nested")" || die "Package runtime layer listing has no readable jar: $nested"
        require_file "$nested_jar"
        while IFS= read -r resource; do
          printf '%s:%s\n' "$nested" "$resource" >> "$auto_config_file"
        done < <(jar tf "$nested_jar" | grep -E 'META-INF/spring\.factories|META-INF/spring/org\.springframework\.boot\.autoconfigure\.AutoConfiguration\.imports' || true)
        ;;
    esac
  done < "$libs_file"

  ai_lib_count="$(grep -Ec '^(ouroboros-ai-|spring-ai-).*\.jar$' "$libs_file" || true)"
  if [ "$ai_lib_count" -gt 0 ]; then
    [ -s "$auto_config_file" ] || die "Package runtime layer contains AI runtime libs but no auto-configuration resources: $artifact"
  else
    die "Package runtime layer contains no AI runtime libs; package hygiene cannot prove AI runtime packaging: $artifact"
  fi

  if grep -Eq 'ouroboros-ai-starter-spring-ai|spring-ai-' "$libs_file" \
    && grep -Eq 'ouroboros-ai-starter-legacy' "$libs_file"; then
    die "Package auto-configuration family is ambiguous: legacy and Spring AI starters are both packaged"
  fi
}

case "$mode" in
  static)
    run_static
    ;;
  resolved)
    run_resolved
    ;;
  package)
    run_package
    ;;
  all)
    run_static
    run_resolved
    run_package
    ;;
  *)
    die "Unsupported mode: $mode"
    ;;
esac

echo "AI runtime hygiene verification passed"
