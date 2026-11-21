#!/usr/bin/env bash
set -euo pipefail
: "${SSO_ISOLATED_FIXTURE:?Select the dedicated acceptance instance}"
: "${SSO_FIXTURE_ROOT:?Set the absolute fixture directory}"
repo_root="$(cd "$(dirname "$0")/../../../../../.." && pwd)"
cd "$repo_root"
mothership_root="$repo_root/platform/ouroboros-mothership"
classpath_file="$mothership_root/target/runtime.classpath"
test -s "$classpath_file"
exec java -Xmx1536m -cp "$mothership_root/target/classes:$(cat "$classpath_file")" com.ouroboros.mothership.OuroborosApplication \
 --spring.config.additional-location="file:$mothership_root/docker/root/app/base/service/application-base.yml,file:$SSO_FIXTURE_ROOT/local.yml" "$@"
