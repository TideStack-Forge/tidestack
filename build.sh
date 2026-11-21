#!/bin/bash
####################################
# bash ./build.sh <build|deploy|publish|publish-mothership|publish-develop|preflight|probe-mothership|probe-mothership-arm64|probe-develop> [options]
# 命令：
#   build：构建并 install maven 项目
#   deploy：构建并 deploy maven 项目
#   publish：构建并发布全部 docker 镜像
#   publish-mothership：构建并发布 mothership 镜像
#   publish-develop：构建并发布 develop 镜像
#   preflight：构建 root 项目，并本地构建 mothership/develop 镜像，不部署 Maven、不推送镜像
#   probe-mothership：构建 amd64 mothership 本地探针镜像，不推送
#   probe-mothership-arm64：构建 arm64 mothership 本地探针镜像，不推送
#   probe-develop：构建 develop 本地探针镜像，不推送
# 参数：
#   -v 版本号
#   -w Web镜像Tag
#   -e Mothership镜像Tag
#   -b Base镜像
#   -t tags
#   -r registry
#   -n namespace
#   -u username
#   -p password
#   -c 是否运行构建阶段测试用例（true/false）
#   -j 是否部署 maven（在使用publish相关命令时）
#   -a 是否发布多架构镜像
# ------
#   -s 状态文件（用于多阶段任务传递状态）
####################################


print_help() {
  cat <<'EOF'
Usage:
  bash ./build.sh <build|deploy|publish|publish-mothership|publish-develop|preflight|probe-mothership|probe-mothership-arm64|probe-develop> [options]
  bash ./build.sh [options]   # default command: build

Commands:
  build               构建并 install Maven 项目
  deploy              构建并 deploy Maven 项目
  publish             构建并发布全部 Docker 镜像
  publish-mothership  构建并发布 mothership 镜像
  publish-develop     构建并发布 develop 镜像
  preflight           构建 root 项目，并本地构建 mothership/develop 镜像，不部署 Maven、不推送镜像
  probe-mothership    构建 amd64 mothership 本地探针镜像，不登录、不推送
  probe-mothership-arm64
                      构建 arm64 mothership 本地探针镜像，不登录、不推送
  probe-develop       构建 amd64 develop 本地探针镜像，不登录、不推送

Options:
  -v <version>     版本号
  -w <tag>         Web 镜像 Tag
  -e <tag>         Mothership 镜像 Tag
  -b <image>       amd64 基础镜像；arm64 probe 自动使用同名 -arm64 镜像
  -t <tags>        发布 tags（逗号分隔）；probe 命令仅接受一个本地 tag
  -r <registry>    镜像仓库地址
  -n <namespace>   镜像仓库命名空间
  -u <username>    镜像仓库用户名
  -p <password>    镜像仓库密码
  -c <true|false>  是否运行构建阶段测试用例
  -j               是否部署 Maven（在 publish 相关命令时）
  -a               是否发布多架构镜像
  -s <file>        状态文件（用于多阶段任务传递状态）
  -h, --help       显示帮助

Examples:
  bash ./build.sh build -v 1.0.0-beta.3 -c false
  bash ./build.sh deploy -v 1.0.0-beta.3 -j -c true
  bash ./build.sh publish-mothership -v 1.0.0 -w latest -t 1.0.0,latest
  bash ./build.sh preflight -v 1.0.0 -w latest -t 1.0.0,latest
  bash ./build.sh probe-mothership -b example/base:tag -t base-image-probe
  bash ./build.sh probe-mothership-arm64 -b example/base:tag -t base-image-probe
  bash ./build.sh probe-develop -t develop-probe
EOF
}


####################################
# 解析 command / help
####################################

# 尝试获取 command
command=$1

if [ "$command" = "help" ] || [ "$command" = "-h" ] || [ "$command" = "--help" ]; then
  print_help
  exit 0
fi

# 如果是支持的 command，则推出第一个参数
if [ "$command" = "build" ] || [ "$command" = "deploy" ] || [ "$command" = "publish" ] || [ "$command" = "publish-mothership" ] || [ "$command" = "publish-develop" ] || [ "$command" = "preflight" ] || [ "$command" = "probe-mothership" ] || [ "$command" = "probe-mothership-arm64" ] || [ "$command" = "probe-develop" ]; then
  shift
else
  command=build
fi

if [ "$1" = "help" ] || [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
  print_help
  exit 0
fi


####################################
# 默认参数
####################################

maven_repo_local="${MAVEN_REPO_LOCAL:-}"
invocation_dir=$(pwd)
# Static analysis is owned by Jenkins verify-* stages; build.sh only packages/deploys artifacts.
static_check_skip_args="-Dcheckstyle.skip=true -Dpmd.skip=true"
mvn_cmd() {
  if [ -n "$maven_repo_local" ]; then
    mvn -Dmaven.repo.local="$maven_repo_local" "$@"
  else
    mvn "$@"
  fi
}

# 默认从 maven 项目中获取版本号
version=$(mvn_cmd help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null || echo "")
web_image_tag="latest"
mothership_image_tag=$version
base_image="registry.cn-hangzhou.aliyuncs.com/ouroboros/eclipse-temurin:21-jdk-noble"
registry="registry.cn-hangzhou.aliyuncs.com"
namespace="ouroboros"
tags=""
publish_mothership=false
publish_develop=false
build_tests_enabled=true
username=""
password=""

# 常量
MOTHERSHIP_IMAGE_NAME="ouroboros-mothership"
DEVELOP_IMAGE_NAME="ouroboros-develop"

# 执行命令出错时退出
set -e

####################################
# 解析参数
####################################
while getopts "hv:w:b:e:t:r:n:u:p:s:c:ja-:" opt; do
  case $opt in
    h)
      print_help
      exit 0
      ;;
    v)
      version=$OPTARG
      ;;
    w)
      web_image_tag=$OPTARG
      ;;
    b)
      base_image=$OPTARG
      ;;
    e)
      mothership_image_tag=$OPTARG
      ;;
    t)
      tags=$OPTARG
      ;;
    r)
      registry=$OPTARG
      ;;
    n)
      namespace=$OPTARG
      ;;
    u)
      username=$OPTARG
      ;;
    p)
      password=$OPTARG
      ;;
    c)
      build_tests_enabled=$OPTARG
      ;;
    s)
      # 固定一下路径，防止有 cd 命令没有退回到脚本所在目录
      if [[ "$OPTARG" = /* ]]; then
        state_file=$OPTARG
      else
        state_file="${invocation_dir}/${OPTARG}"
      fi
      ;;
    j)
      deploy_maven=true
      ;;
    a)
      support_multi_arch=true
      ;;
    -)
      if [ "$OPTARG" = "help" ]; then
        print_help
        exit 0
      fi
      echo "未知参数 --$OPTARG"
      exit 1
      ;;
    ?)
      echo "未知参数"
      exit 1
      ;;
  esac
done


# 衍生参数
web_image_amd64=$registry/$namespace/ouroboros-web:$web_image_tag
web_image_arm64=${web_image_amd64}-arm64
web_image=$web_image_amd64
base_image_amd64=${base_image}
base_image_arm64=${base_image}-arm64
motherhip_image_amd64=$registry/$namespace/$MOTHERSHIP_IMAGE_NAME:$mothership_image_tag
motherhip_image_arm64=$registry/$namespace/$MOTHERSHIP_IMAGE_NAME:$mothership_image_tag-arm64
motherhip_image=$motherhip_image_amd64


####################################
# 构建状态
####################################
built_root=false
deployed_root=false
built_mothership=false
built_develop=false
logon_registry=false

state_repo_root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
state_head=$(git rev-parse HEAD 2>/dev/null || echo "no-git")
state_worktree_fingerprint=""
mothership_context_fingerprint=""
develop_context_fingerprint=""

sha256_stream() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 | awk '{print $1}'
  else
    echo "缺少 sha256sum 或 shasum，无法校验构建状态" >&2
    return 1
  fi
}

sha256_file() {
  local file_path=$1
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file_path" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file_path" | awk '{print $1}'
  else
    echo "缺少 sha256sum 或 shasum，无法校验构建状态" >&2
    return 1
  fi
}

file_mode() {
  local file_path=$1
  if stat -f '%Lp' "$file_path" >/dev/null 2>&1; then
    stat -f '%Lp' "$file_path"
  else
    stat -c '%a' "$file_path"
  fi
}

append_path_fingerprint() {
  local file_path=$1
  printf 'path=%s\n' "$file_path"
  printf 'mode=%s\n' "$(file_mode "$file_path")"
  if [ -L "$file_path" ]; then
    printf 'link=%s\n' "$(readlink "$file_path")"
  else
    printf 'sha256=%s\n' "$(sha256_file "$file_path")"
  fi
}

calculate_context_fingerprint() {
  local context_dir=$1
  if [ ! -d "$context_dir" ]; then
    echo "Docker 构建上下文不存在：$context_dir" >&2
    return 1
  fi

  (
    set -o pipefail
    (
      cd "$context_dir"
      find . \( -type f -o -type l \) -print | LC_ALL=C sort | while IFS= read -r file_path; do
        append_path_fingerprint "$file_path"
      done
    ) | sha256_stream
  )
}

calculate_worktree_fingerprint() {
  if [ "$state_head" = "no-git" ]; then
    printf '%s\n' "no-git"
    return
  fi

  (
    set -o pipefail
    (
      cd "$state_repo_root"
      printf 'head=%s\n' "$state_head"
      git diff --binary --no-ext-diff "$state_head" -- \
        . \
        ':(exclude).jenkins-last-stage.log' \
        ':(exclude).codestable/**' \
        ':(exclude)platform/ouroboros-mothership/docker/build-layer/**' \
        ':(exclude)platform/ouroboros-mothership/docker/common-layer/**' \
        ':(exclude)platform/ouroboros-develop/docker/build-layer/**' \
        ':(exclude)platform/ouroboros-develop/docker/template-layer/**' \
        ':(exclude)platform/ouroboros-develop/docker/common-layer/**'
      while IFS= read -r -d '' file_path; do
        case "$file_path" in
          .jenkins-last-stage.log|.m2/*|.codestable/*|platform/ouroboros-mothership/docker/build-layer/*|platform/ouroboros-mothership/docker/common-layer/*|platform/ouroboros-develop/docker/build-layer/*|platform/ouroboros-develop/docker/template-layer/*|platform/ouroboros-develop/docker/common-layer/*)
            continue
            ;;
        esac
        if [ -n "$state_file" ] && [ "${state_repo_root}/${file_path}" = "$state_file" ]; then
          continue
        fi
        append_path_fingerprint "$file_path"
      done < <(git ls-files --others --exclude-standard -z)
    ) | sha256_stream
  )
}

read_state_file() {
  local saved_format=""
  local saved_repo_root=""
  local saved_head=""
  local saved_version=""
  local saved_worktree_fingerprint=""
  local saved_mothership_context_fingerprint=""
  local saved_develop_context_fingerprint=""
  local saved_built_root=false
  local saved_deployed_root=false
  local saved_built_mothership=false
  local saved_built_develop=false
  local saved_logon_registry=false
  local key
  local value

  while IFS='=' read -r key value; do
    case "$key" in
      state_format)
        saved_format=$value
        ;;
      repo_root)
        saved_repo_root=$value
        ;;
      head)
        saved_head=$value
        ;;
      version)
        saved_version=$value
        ;;
      worktree_fingerprint)
        saved_worktree_fingerprint=$value
        ;;
      mothership_context_fingerprint)
        saved_mothership_context_fingerprint=$value
        ;;
      develop_context_fingerprint)
        saved_develop_context_fingerprint=$value
        ;;
      built_root)
        saved_built_root=$value
        ;;
      deployed_root)
        saved_deployed_root=$value
        ;;
      built_mothership)
        saved_built_mothership=$value
        ;;
      built_develop)
        saved_built_develop=$value
        ;;
      logon_registry)
        saved_logon_registry=$value
        ;;
      "")
        ;;
      *)
        echo "状态文件包含未知字段：$key" >&2
        exit 1
        ;;
    esac
  done < "$state_file"

  if [ "$saved_format" != "2" ] ||
     [ "$saved_repo_root" != "$state_repo_root" ] ||
     [ "$saved_head" != "$state_head" ] ||
     [ "$saved_version" != "$version" ]; then
    echo "状态文件与当前 worktree、HEAD 或版本不匹配：$state_file" >&2
    exit 1
  fi

  for value in "$saved_built_root" "$saved_deployed_root" "$saved_built_mothership" "$saved_built_develop" "$saved_logon_registry"; do
    if [ "$value" != "true" ] && [ "$value" != "false" ]; then
      echo "状态文件包含非法布尔值：$value" >&2
      exit 1
    fi
  done

  if [ "$saved_deployed_root" = true ] && [ "$saved_built_root" != true ]; then
    echo "状态文件阶段关系非法：deployed_root 依赖 built_root" >&2
    exit 1
  fi
  if { [ "$saved_built_mothership" = true ] || [ "$saved_built_develop" = true ]; } &&
     [ "$saved_built_root" != true ]; then
    echo "状态文件阶段关系非法：镜像上下文依赖 built_root" >&2
    exit 1
  fi

  if [ "$saved_built_root" = true ] ||
     [ "$saved_deployed_root" = true ] ||
     [ "$saved_built_mothership" = true ] ||
     [ "$saved_built_develop" = true ]; then
    if [ -z "$saved_worktree_fingerprint" ]; then
      echo "状态文件缺少工作树指纹" >&2
      exit 1
    fi
    local current_worktree_fingerprint
    current_worktree_fingerprint=$(calculate_worktree_fingerprint)
    if [ "$saved_worktree_fingerprint" != "$current_worktree_fingerprint" ]; then
      echo "状态文件对应的源码工作树已变化，拒绝复用构建阶段" >&2
      exit 1
    fi
  elif [ -n "$saved_worktree_fingerprint" ]; then
    echo "状态文件阶段关系非法：未构建却包含工作树指纹" >&2
    exit 1
  fi

  if [ "$saved_built_mothership" = true ]; then
    if [ -z "$saved_mothership_context_fingerprint" ]; then
      echo "状态文件缺少 mothership Docker 上下文指纹" >&2
      exit 1
    fi
    local current_mothership_context_fingerprint
    current_mothership_context_fingerprint=$(calculate_context_fingerprint "${state_repo_root}/platform/ouroboros-mothership/docker")
    if [ "$saved_mothership_context_fingerprint" != "$current_mothership_context_fingerprint" ]; then
      echo "mothership Docker 上下文已变化，拒绝复用构建阶段" >&2
      exit 1
    fi
  elif [ -n "$saved_mothership_context_fingerprint" ]; then
    echo "状态文件阶段关系非法：未构建 mothership 却包含上下文指纹" >&2
    exit 1
  fi

  if [ "$saved_built_develop" = true ]; then
    if [ -z "$saved_develop_context_fingerprint" ]; then
      echo "状态文件缺少 develop Docker 上下文指纹" >&2
      exit 1
    fi
    local current_develop_context_fingerprint
    current_develop_context_fingerprint=$(calculate_context_fingerprint "${state_repo_root}/platform/ouroboros-develop/docker")
    if [ "$saved_develop_context_fingerprint" != "$current_develop_context_fingerprint" ]; then
      echo "develop Docker 上下文已变化，拒绝复用构建阶段" >&2
      exit 1
    fi
  elif [ -n "$saved_develop_context_fingerprint" ]; then
    echo "状态文件阶段关系非法：未构建 develop 却包含上下文指纹" >&2
    exit 1
  fi

  state_worktree_fingerprint=$saved_worktree_fingerprint
  mothership_context_fingerprint=$saved_mothership_context_fingerprint
  develop_context_fingerprint=$saved_develop_context_fingerprint
  built_root=$saved_built_root
  deployed_root=$saved_deployed_root
  built_mothership=$saved_built_mothership
  built_develop=$saved_built_develop
  logon_registry=$saved_logon_registry
}

write_state_file() {
  local state_dir
  local temp_state_file

  state_dir=$(dirname "$state_file")
  if [ ! -d "$state_dir" ]; then
    echo "状态文件目录不存在：$state_dir" >&2
    exit 1
  fi

  if [ "$built_root" = true ] ||
     [ "$deployed_root" = true ] ||
     [ "$built_mothership" = true ] ||
     [ "$built_develop" = true ]; then
    state_worktree_fingerprint=$(calculate_worktree_fingerprint)
  else
    state_worktree_fingerprint=""
  fi
  if [ "$built_mothership" = true ]; then
    mothership_context_fingerprint=$(calculate_context_fingerprint "${state_repo_root}/platform/ouroboros-mothership/docker")
  else
    mothership_context_fingerprint=""
  fi
  if [ "$built_develop" = true ]; then
    develop_context_fingerprint=$(calculate_context_fingerprint "${state_repo_root}/platform/ouroboros-develop/docker")
  else
    develop_context_fingerprint=""
  fi

  temp_state_file="${state_file}.tmp.$$"
  umask 077
  {
    echo "state_format=2"
    echo "repo_root=$state_repo_root"
    echo "head=$state_head"
    echo "version=$version"
    echo "worktree_fingerprint=$state_worktree_fingerprint"
    echo "mothership_context_fingerprint=$mothership_context_fingerprint"
    echo "develop_context_fingerprint=$develop_context_fingerprint"
    echo "built_root=$built_root"
    echo "deployed_root=$deployed_root"
    echo "built_mothership=$built_mothership"
    echo "built_develop=$built_develop"
    echo "logon_registry=$logon_registry"
  } > "$temp_state_file"
  mv "$temp_state_file" "$state_file"
}

# 读取状态文件
if [ -n "$state_file" ]; then
  if [ -f "$state_file" ]; then
    read_state_file
  fi
fi


# 到这里如果版本号为空则报错
if [ -z "$version" ]; then
  echo "版本号为空！"
  exit 1
fi

####################################
# 辅助函数
####################################

# 根据 镜像名、tags、tag后缀 生成目标参数
# generate_targets image_name tags tag_affix
generate_targets() {
  local image_name=$1
  local tags=$2
  local tag_affix=$3

  # tags 是 以逗号分隔的字符串
  local targets=""
  for tag in $(echo "$tags" | tr ',' '\n'); do
    targets="$targets -t $registry/$namespace/$image_name:$tag$tag_affix"
  done
  echo "$targets"
}

# 根据 版本号 生成 tags
# generate_tags version
generate_tags() {
  local full_version=$1
  local base_tags=$full_version
  # 有可能是 1.0.0+001这样的格式，要区分出版本部分和build部分
  local version=$(echo $full_version | cut -d '+' -f 1)
  local build=""
  if [[ $full_version =~ .*\+.* ]]; then
    base_tags="$base_tags,$version"
    build=$(echo "$full_version" | cut -d '+' -f 2)
  fi

  # 如果是 1.0.0 格式，即正式版本，发布 tags 为 版本号、major-latest、major.minor-latest、latest
  if [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    local major=$(echo $version | cut -d '.' -f 1)
    local minor=$(echo $version | cut -d '.' -f 2)
    local patch=$(echo $version | cut -d '.' -f 3)
    echo "$base_tags,$major-latest,$major.$minor-latest,latest"

  # 有 -SNAPSHOT 或 -prerelease 后缀，即临时版本，只发布具体版本
  elif [[ ${version} =~ ^.*-(SNAPSHOT|PRERELEASE|prerelease) ]]; then
    echo "$base_tags"

  # 有 -hotfix[.digit] 后缀，即热修复版本，发布原始版本和 hotfix 版本
  elif [[ ${version,,} =~ ^.*-hotfix(\.[0-9]+)?$ ]]; then
    # 原始版本号，去掉末尾的 -hotfix(HOTFIX)[.digit] 后缀
    local original_version=$(echo $version | sed 's/-hotfix.*$//I')
    echo "$base_tags,$original_version"

  # 有 -alpha、-beta、-rc 后缀，发布 tags 为 版本号、major.minor.patch-(alpha|beta|rc)-latest
  elif [[ ${version,,} =~ ^.*-(alpha|beta|rc)(\.[0-9]+)?$ ]]; then
    local major=$(echo $version | cut -d '.' -f 1)
    local minor=$(echo $version | cut -d '.' -f 2)
    local patch=$(echo $version | cut -d '.' -f 3)
    local flag=$(echo $version | cut -d '-' -f 2)

    # 去掉先行版本的数字版本
    local flag=$(echo $flag | cut -d '.' -f 1)
    echo "$base_tags,$major.$minor.$patch-$flag-latest"

  # 其他情况，发布 tags 为 版本号
  else
    echo "$base_tags"
  fi
}

# 登录 docker registry，无需参数
login_docker_registry() {
  if [ $logon_registry = true ]; then
    return
  fi
  if [ -z "$username" ] || [ -z "$password" ]; then
    echo "未设置用户名或密码，跳过登录"
    logon_registry=true
    return
  fi
  echo "正在登录 $registry"
  # 登录 docker registry
  echo "$password" | docker login $registry -u "$username" --password-stdin
  logon_registry=true
}

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

json_string_or_null() {
  local value=$1
  if [ -z "$value" ]; then
    printf 'null'
  else
    printf '"%s"' "$(json_escape "$value")"
  fi
}

sha256_file() {
  local file=$1
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    shasum -a 256 "$file" | awk '{print $1}'
  fi
}

append_runtime_manifest_entry() {
  local manifest_file=$1
  local group_id=$2
  local artifact_id=$3
  local artifact_version=$4
  local jar_file=$5
  local category=$6
  local first_entry=$7
  local include_sha256=$8
  local file_name
  local sha256

  file_name=$(basename "$jar_file")

  if [ "$first_entry" != true ]; then
    printf ',\n' >> "$manifest_file"
  fi

  cat >> "$manifest_file" <<EOF
    {
      "groupId": $(json_string_or_null "$group_id"),
      "artifactId": $(json_string_or_null "$artifact_id"),
      "version": $(json_string_or_null "$artifact_version"),
      "fileName": "$(json_escape "$file_name")",
EOF

  if [ "$include_sha256" = true ]; then
    sha256=$(sha256_file "$jar_file")
    cat >> "$manifest_file" <<EOF
      "sha256": "$(json_escape "$sha256")",
EOF
  fi

  cat >> "$manifest_file" <<EOF
      "category": "$(json_escape "$category")"
    }
EOF
}

runtime_manifest_sha256_enabled() {
  [ "${GIT_BRANCH:-}" != "" ] && [[ "${GIT_BRANCH}" == refs/tags/* ]]
}

normalize_runtime_dependency_list() {
  sed -E $'s/\x1B\\[[0-9;]*[A-Za-z]//g; s/[[:space:]]+-- module.*$//; s/^[[:space:]]+//; s/[[:space:]]+$//'
}

generate_runtime_dependency_list() {
  local dependency_list_file=$1

  mkdir -p "$(dirname "$dependency_list_file")"
  mvn_cmd org.apache.maven.plugins:maven-dependency-plugin:3.6.0:list \
    -o \
    -DoutputFile="${dependency_list_file}" \
    -DoutputAbsoluteArtifactFilename=true \
    -DincludeTypes=jar \
    -DincludeScope=runtime \
    -Dsort=true \
    -Dstyle.color=never
}

count_runtime_jars() {
  local third_party_lib_path=$1
  local ouroboros_lib_path=$2

  find "$third_party_lib_path" "$ouroboros_lib_path" -maxdepth 1 -type f -name "*.jar" 2>/dev/null | wc -l | tr -d ' '
}

generate_runtime_manifest() {
  local manifest_file=$1
  local platform_version=$2
  local dependency_list_file=$3
  local third_party_lib_path=$4
  local ouroboros_lib_path=$5
  local generated_at
  local first_entry=true
  local include_sha256=false
  local expected_entry_count
  local actual_entry_count=0
  local missing_entry_count=0
  local line
  local artifact_path
  local coordinate
  local artifact_version
  local group_id
  local rest
  local artifact_id
  local artifact_type
  local category
  local lib_path
  local copied_jar_file
  local seen_copied_jar_files="|"

  generated_at=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  mkdir -p "$(dirname "$manifest_file")"
  expected_entry_count=$(count_runtime_jars "$third_party_lib_path" "$ouroboros_lib_path")
  if runtime_manifest_sha256_enabled; then
    include_sha256=true
  fi

  cat > "$manifest_file" <<EOF
{
  "schemaVersion": 1,
  "platform": "ouroboros",
  "runtime": "mothership",
  "platformVersion": "$(json_escape "$platform_version")",
  "generatedAt": "$(json_escape "$generated_at")",
  "entries": [
EOF

  while IFS= read -r line; do
    case "$line" in
      *:jar:*:/*) ;;
      *) continue ;;
    esac

    artifact_path=${line##*:}
    coordinate=${line%:*}
    coordinate=${coordinate%:*}
    artifact_version=${coordinate##*:}
    coordinate=${coordinate%:*}
    group_id=${coordinate%%:*}
    rest=${coordinate#*:}
    artifact_id=${rest%%:*}
    rest=${rest#*:}
    artifact_type=${rest%%:*}
    if [ "$artifact_type" != "jar" ] || [ -z "$group_id" ] || [ -z "$artifact_id" ] || [ -z "$artifact_version" ]; then
      continue
    fi

    if [ "$group_id" = "com.ouroboros" ]; then
      category="ouroboros"
      lib_path=$ouroboros_lib_path
    else
      category="third-party"
      lib_path=$third_party_lib_path
    fi

    copied_jar_file="$lib_path/$(basename "$artifact_path")"
    if [ ! -f "$copied_jar_file" ]; then
      echo "runtime manifest dependency is not copied: $line" >&2
      missing_entry_count=$((missing_entry_count + 1))
      continue
    fi
    case "$seen_copied_jar_files" in
      *"|$copied_jar_file|"*)
        continue
        ;;
    esac
    seen_copied_jar_files="${seen_copied_jar_files}${copied_jar_file}|"

    append_runtime_manifest_entry "$manifest_file" "$group_id" "$artifact_id" "$artifact_version" "$copied_jar_file" "$category" "$first_entry" "$include_sha256"
    first_entry=false
    actual_entry_count=$((actual_entry_count + 1))
  done < <(normalize_runtime_dependency_list < "$dependency_list_file")

  cat >> "$manifest_file" <<EOF

  ]
}
EOF

  if [ "$missing_entry_count" -ne 0 ] || [ "$actual_entry_count" -ne "$expected_entry_count" ]; then
    echo "runtime manifest entry count mismatch: expected copied jars=$expected_entry_count, actual manifest entries=$actual_entry_count, missing dependency entries=$missing_entry_count" >&2
    exit 1
  fi
}

# 构建并推送镜像
# build_push_image build_args targets
build_push_image() {
  build_args=$1
  targets=$2
  login_docker_registry

  echo "构建并推送：$targets"
  echo "构建参数：$build_args"

  # 若支持 buildx，则使用 buildx 构建镜像
  if [ -n "$(docker buildx version | grep 'v[0-9][0-9]*')" ]; then
    docker buildx build $build_args $targets --push .
  else
    if [ $support_multi_arch = true ]; then
      echo "buildx 未安装，不支持发布多架构镜像!"
      exit 1
    fi
    docker build $build_args $targets .
    for target in $(echo "$targets" | tr '-t ' '\n'); do
      # 删除空白
      target=$(echo $target | tr -d '[:space:]')
      # 非空则推送
      if [ -n "$target" ]; then
        docker push $target
      fi
    done
  fi
}

# 构建本地镜像，不登录镜像仓库、不推送
# build_local_image build_args targets
build_local_image() {
  local build_args=$1
  local targets=$2

  echo "构建本地镜像：$targets"
  echo "构建参数：$build_args"

  if [ -n "$(docker buildx version | grep 'v[0-9][0-9]*')" ]; then
    # 本地探针不继承全局选中的 docker-container builder，避免额外的 BuildKit 拉取依赖。
    local buildx_builder
    buildx_builder=$(docker context show)
    docker buildx build --builder "$buildx_builder" $build_args $targets --load .
  else
    docker build $build_args $targets .
  fi
}

ensure_csv_contains() {
  local csv=$1
  local required=$2

  if [ -z "$csv" ]; then
    echo "$required"
    return
  fi

  for item in $(echo "$csv" | tr ',' '\n'); do
    if [ "$item" = "$required" ]; then
      echo "$csv"
      return
    fi
  done

  echo "$csv,$required"
}

####################################
# 步骤函数
####################################

# build root
build_root() {
  # 如果 had_install 变量为 true，则直接返回
  if [ "$built_root" = true ]; then
    return
  fi

  local test_args=""
  if [ "$build_tests_enabled" != true ]; then
    echo "跳过 build 阶段测试用例"
    test_args="-DskipTests"
  fi

  # 设置 version
  mvn_cmd versions:set -DnewVersion="${version}" -DgenerateBackupPoms=false
  cd bom && mvn_cmd clean && mvn_cmd install ${static_check_skip_args} ${test_args} && cd ..
  mvn_cmd clean
  mvn_cmd install -P 'jdk21' -Djacoco.skip=true ${static_check_skip_args} ${test_args}

  built_root=true
}

# deploy root
deploy_root() {
  if [ "$deployed_root" = true ]; then return; fi
  build_root
  cd bom && \
    mvn_cmd deploy -DskipTests -Dmaven.compile.skip=true \
      ${static_check_skip_args} \
      -Dmaven.jar.skip=true -Dmaven.install.skip=true \
      -Ddeploy.release.url=${MAVEN_DEPLOY_RELEASE_URL:-http://maven.whxinpai.com:8202/nexus/content/repositories/releases/} \
      -Ddeploy.snapshot.url=${MAVEN_DEPLOY_SNAPSHOT_URL:-http://maven.whxinpai.com:8202/nexus/content/repositories/snapshots/} \
    && cd ..
  mvn_cmd -T 2C deploy -DskipTests -Dmaven.compile.skip=true \
    ${static_check_skip_args} \
    -Dmaven.package.skip=true -Dmaven.install.skip=true \
    -Ddeploy.release.url=${MAVEN_DEPLOY_RELEASE_URL:-http://maven.whxinpai.com:8202/nexus/content/repositories/releases/} \
    -Ddeploy.snapshot.url=${MAVEN_DEPLOY_SNAPSHOT_URL:-http://maven.whxinpai.com:8202/nexus/content/repositories/snapshots/}
  deployed_root=true
}

# 构造 ouroboros-mothership
build_mothership() {
  if [ $built_mothership = true ]; then return; fi

  # 如果需要deploy maven
  if [ "$deploy_maven" = true ]; then
    deploy_root
  else
    build_root
  fi

  # 进入 mothership 项目
  cd platform/ouroboros-mothership

  local third_party_lib_path="$(pwd)/docker/common-layer/app/base/service/third-party-lib"
  local ouroboros_lib_path="$(pwd)/docker/build-layer/app/base/service/ouroboros-lib"
  local runtime_manifest_path="$(pwd)/docker/build-layer/app/base/service/manifest.json"
  local runtime_dependency_list_path="$(pwd)/target/runtime-manifest-dependencies.txt"

  mvn_cmd org.apache.maven.plugins:maven-dependency-plugin:3.6.0:help

  ## 当已经 deploy 过，需要检查 version 是不是 -SNAPSHOT 结尾（必须为大写），如果是则需要运行 dependency:resolve
  if [ "$deployed_root" = true ] && echo "$version" | grep -q -- '-SNAPSHOT'; then
    mvn_cmd org.apache.maven.plugins:maven-dependency-plugin:3.6.0:resolve
  fi

  ## 构建项目，并将 jar 包拷贝到 docker 目录
  #  mvn package -o -Dmaven.test.skip=true -Dmaven.compile.skip=true -Dmaven.jar.skip=true -Dmaven.install.skip=true

  # 清空发布目录
  echo "清空发布目录"
  rm -rf ./docker/build-layer/
  rm -rf ./docker/common-layer/

  ## 拷贝所有依赖包
  # 限制为非 com.ouroboros 组的依赖
  mvn_cmd org.apache.maven.plugins:maven-dependency-plugin:3.6.0:copy-dependencies -o -DincludeScope=runtime -DoutputDirectory="${third_party_lib_path}" -DexcludeGroupIds=com.ouroboros
  # 限制为 com.ouroboros 组的依赖
  mvn_cmd org.apache.maven.plugins:maven-dependency-plugin:3.6.0:copy-dependencies -o -DincludeScope=runtime -DoutputDirectory="${ouroboros_lib_path}" -DincludeGroupIds=com.ouroboros

  # 删除 *.pom 文件
  rm -f ${third_party_lib_path}/*.pom
  rm -f ${ouroboros_lib_path}/*.pom

  generate_runtime_dependency_list "$runtime_dependency_list_path"
  generate_runtime_manifest "$runtime_manifest_path" "$version" "$runtime_dependency_list_path" "$third_party_lib_path" "$ouroboros_lib_path"

  cp "target/ouroboros-mothership-${version}.jar" ./docker/build-layer/app/base/service/ouroboros-mothership.jar

  built_mothership=true
  cd -
}

# 校验本地构建参数，避免本地命令触发部署
validate_local_build_options() {
  if [ "${deploy_maven:-false}" = true ]; then
    echo "本地构建命令不允许使用 -j，避免部署 Maven 产物" >&2
    exit 2
  fi
}

# 校验本地探针参数，避免无效构建
validate_probe_options() {
  validate_local_build_options
  if [[ "$tags" == *,* ]]; then
    echo "probe 命令仅接受一个本地 tag" >&2
    exit 2
  fi
}

# 构建 amd64 mothership 本地探针镜像
probe_mothership() {
  validate_probe_options
  build_mothership

  cd platform/ouroboros-mothership/docker
  local probe_tag=${tags:-base-image-probe}
  local target="${MOTHERSHIP_IMAGE_NAME}:${probe_tag}"
  local build_args="--build-arg BASE_IMAGE=${base_image_amd64} --build-arg WEB_IMAGE=${web_image_amd64} --platform linux/amd64"
  build_local_image "$build_args" "-t $target"
  echo "本地探针镜像：$target"
  cd -
}

# 构建 arm64 mothership 本地探针镜像
probe_mothership_arm64() {
  validate_probe_options
  build_mothership

  cd platform/ouroboros-mothership/docker
  local probe_tag=${tags:-base-image-probe}
  local target="${MOTHERSHIP_IMAGE_NAME}:${probe_tag}-arm64"
  local build_args="--build-arg BASE_IMAGE=${base_image_arm64} --build-arg WEB_IMAGE=${web_image_arm64} --platform linux/arm64"
  build_local_image "$build_args" "-t $target"
  echo "本地探针镜像：$target"
  cd -
}

# 本地构建 mothership 镜像，不登录、不推送
preflight_mothership() {
  validate_local_build_options
  build_mothership

  cd platform/ouroboros-mothership/docker
  local local_tags="${tags:-$(generate_tags "$version")}"
  local_tags=$(ensure_csv_contains "$local_tags" "$mothership_image_tag")
  local amd64_targets=$(generate_targets $MOTHERSHIP_IMAGE_NAME "$local_tags")
  local arm64_targets=$(generate_targets $MOTHERSHIP_IMAGE_NAME "$local_tags" "-arm64")
  local amd64_build_args="--build-arg BASE_IMAGE=${base_image_amd64} --build-arg WEB_IMAGE=${web_image_amd64} --platform linux/amd64"
  local arm64_build_args="--build-arg BASE_IMAGE=${base_image_arm64} --build-arg WEB_IMAGE=${web_image_arm64} --platform linux/arm64"
  build_local_image "$amd64_build_args" "$amd64_targets"
  build_local_image "$arm64_build_args" "$arm64_targets"
  echo "mothership 本地预检镜像标签：$local_tags"
  cd -
}

# 发布 ouroboros-mothership
publish_mothership() {
  build_mothership

  cd platform/ouroboros-mothership/docker
  local tags="${tags}"
  if [ -z "$tags" ]; then
    tags=$(generate_tags "$version")
  fi

  # 如果支持多架构镜像，则一次性构建 amd64、arm64 镜像
  if [ "$support_multi_arch" = true ]; then
    local targets=$(generate_targets $MOTHERSHIP_IMAGE_NAME "$tags")
    local build_args="--build-arg BASE_IMAGE=${base_image} --build-arg WEB_IMAGE=${web_image} --platform linux/amd64,linux/arm64"
    build_push_image "$build_args" "$targets"
  # 否则依次构建 amd64、arm64 镜像
  else
    local amd64_target=$(generate_targets $MOTHERSHIP_IMAGE_NAME "$tags")
    local arm64_target=$(generate_targets $MOTHERSHIP_IMAGE_NAME "$tags" "-arm64")
    local amd64_build_args="--build-arg BASE_IMAGE=${base_image_amd64} --build-arg WEB_IMAGE=${web_image_amd64} --platform linux/amd64"
    local arm64_build_args="--build-arg BASE_IMAGE=${base_image_arm64} --build-arg WEB_IMAGE=${web_image_arm64} --platform linux/arm64"
    build_push_image "$amd64_build_args" "$amd64_target"
    build_push_image "$arm64_build_args" "$arm64_target"
  fi
  cd -
}

# 构造 ouroboros-develop
build_develop() {
  build_mothership

  cd platform/ouroboros-develop
  local common_layer_path="$(pwd)/docker/common-layer"
  local build_layer_path="$(pwd)/docker/build-layer"
  local template_layer_path="$(pwd)/docker/template-layer"
  local common_layer_app_extend_path="${common_layer_path}/app/metadata/extend"
  local build_layer_app_extend_path="${build_layer_path}/app/metadata/extend"
  local develop_support_path="${build_layer_path}/app/develop-support"
  local dev_docs_path="${build_layer_path}/app/base/web/dev-docs"
  local project_templates_path="${template_layer_path}/app/project-templates"

  ## 当已经 deploy 过，需要检查 version 是不是 -SNAPSHOT 结尾（必须为大写），如果是则需要运行 dependency:resolve
  if [ "$deployed_root" = true ] && echo "$version" | grep -q -- '-SNAPSHOT'; then
    mvn_cmd org.apache.maven.plugins:maven-dependency-plugin:3.6.0:resolve
  fi

  # 清空发布目录
  echo "清空发布目录"
  rm -rf "${common_layer_path}"
  rm -rf "${build_layer_path}"
  rm -rf "${template_layer_path}"

  ## 删除过时文件，执行一次后删掉
  rm -rf ./docker/root/app/develop-support/
  rm -rf ./docker/root/app/metadata/extend/

  mvn_cmd org.apache.maven.plugins:maven-dependency-plugin:3.6.0:copy-dependencies -o -DincludeScope=runtime -DoutputDirectory="${common_layer_app_extend_path}" -DexcludeGroupIds=com.ouroboros
  mvn_cmd org.apache.maven.plugins:maven-dependency-plugin:3.6.0:copy-dependencies -o -DincludeScope=runtime -DoutputDirectory="${build_layer_app_extend_path}" -DincludeGroupIds=com.ouroboros

  # 删除 *.pom 文件
  rm -f ${common_layer_app_extend_path}/*.pom
  rm -f ${build_layer_app_extend_path}/*.pom

  # 删除 motheship 中已经存在的依赖
  find ../ouroboros-mothership/docker/common-layer/app/base/service/third-party-lib -name "*.*" | while read -r file; do
    local name=$(basename "$file")
    rm -f "${common_layer_app_extend_path}/$name"
  done

  rm -f "${common_layer_app_extend_path}/flowable-event-registry-6.8.1.jar"

  find ../ouroboros-mothership/docker/build-layer/app/base/service/ouroboros-lib -name "*.*" | while read -r file; do
    local name=$(basename "$file")
    rm -f "${build_layer_app_extend_path}/$name"
  done

  cd -

  ## 拷贝 dev-helper
  bash scripts/build-handbook-dev-docs.sh "${dev_docs_path}"

  local helper_path="dev-module/ouroboros-dev-helper"
  local logicflow_debug_helper_path="core/ouroboros-logicflow/ouroboros-logicflow-debug-helper"
  #  mvn package -o -f dev-module/ouroboros-dev-helper/pom.xml -P 'jdk21' -Dmaven.test.skip=true
  mkdir -p "${develop_support_path}"
  cp "${helper_path}/ouroboros-dev-helper-core/target/ouroboros-dev-helper-core-${version}.jar" "${develop_support_path}/ouroboros-dev-helper-core.jar"
  cp "${helper_path}/ouroboros-dev-helper-runtime/target/ouroboros-dev-helper-runtime-${version}.jar" "${develop_support_path}/ouroboros-dev-helper-runtime.jar"
  cp "libs/common/common-dev/target/common-dev-${version}.jar" "${develop_support_path}/ouroboros-common-dev.jar"
  cp "${logicflow_debug_helper_path}/target/ouroboros-logicflow-debug-helper-${version}.jar" "${develop_support_path}/ouroboros-logicflow-debug-helper.jar"

  echo "生成项目模板发布产物"
  ensure_frontend_template_dependencies
  bash project-templates/scripts/verify-frontend-templates.sh --qualification --clean
  bash project-templates/scripts/verify-backend-templates.sh --qualification --clean --platform-smoke
  bash project-templates/scripts/verify-template-publication.sh --clean

  echo "拷贝项目模板"
  rm -rf "${project_templates_path}"
  mkdir -p "${project_templates_path}/catalog" "${project_templates_path}/artifacts"
  cp "project-templates/dist/catalog/stable-catalog.json" \
    "${project_templates_path}/catalog/stable-catalog.json"
  cp -R "project-templates/dist/artifacts/." "${project_templates_path}/artifacts/"

  built_develop=true
}

ensure_frontend_template_dependencies() {
  local frontend_root_node_modules="frontends/web/node_modules"
  local frontend_main_node_modules="frontends/web/main-app/node_modules"

  if [ -d "$frontend_root_node_modules" ] && [ -d "$frontend_main_node_modules" ]; then
    return
  fi

  bash scripts/ci/verify-node-npm-baseline.sh

  if [ ! -d "$frontend_root_node_modules" ]; then
    echo "安装项目模板校验所需前端工作区依赖"
    (
      cd frontends/web
      npm ci --legacy-peer-deps
    )
  fi

  if [ ! -d "$frontend_main_node_modules" ]; then
    echo "安装项目模板校验所需主前端依赖"
    (
      cd frontends/web/main-app
      npm ci --legacy-peer-deps
    )
  fi
}

# 本地构建 ouroboros-develop 镜像，不登录、不推送
preflight_develop() {
  validate_local_build_options
  build_develop

  cd platform/ouroboros-develop/docker
  local local_tags="${tags:-$(generate_tags "$version")}"
  local amd64_targets=$(generate_targets $DEVELOP_IMAGE_NAME "$local_tags")
  local arm64_targets=$(generate_targets $DEVELOP_IMAGE_NAME "$local_tags" "-arm64")
  local amd64_build_args="--build-arg BASE_IMAGE=${motherhip_image_amd64} --platform linux/amd64"
  local arm64_build_args="--build-arg BASE_IMAGE=${motherhip_image_arm64} --platform linux/arm64"
  build_local_image "$amd64_build_args" "$amd64_targets"
  build_local_image "$arm64_build_args" "$arm64_targets"
  echo "develop 本地预检镜像标签：$local_tags"
  cd -
}

# 本地预检发布链路：构建 Maven 产物和 Docker 镜像，不 deploy、不 push
preflight_release() {
  validate_local_build_options
  preflight_mothership
  preflight_develop
}

# 构建 amd64 develop 本地探针镜像
probe_develop() {
  validate_probe_options
  preflight_mothership
  build_develop

  cd platform/ouroboros-develop/docker
  local probe_tag=${tags:-develop-probe}
  local target="${DEVELOP_IMAGE_NAME}:${probe_tag}"
  local build_args="--build-arg BASE_IMAGE=${motherhip_image_amd64} --platform linux/amd64"
  build_local_image "$build_args" "-t $target"
  echo "本地探针镜像：$target"
  cd -
}

# 发布 ouroboros-develop
publish_develop() {
  build_develop

  cd platform/ouroboros-develop/docker
  local tags=${tags:-$(generate_tags "$version")}

  # 如果支持多架构镜像，则一次性构建 amd64、arm64 镜像
  if [ "$support_multi_arch" = true ]; then
    local targets=$(generate_targets $DEVELOP_IMAGE_NAME "$tags")
    local build_args="--build-arg BASE_IMAGE=${motherhip_image} --platform linux/amd64,linux/arm64"
    build_push_image "$build_args" "$targets"
  # 否则依次构建 amd64、arm64 镜像
  else
    local amd64_target=$(generate_targets $DEVELOP_IMAGE_NAME "$tags")
    local arm64_target=$(generate_targets $DEVELOP_IMAGE_NAME "$tags" "-arm64")

    local amd64_build_args="--build-arg BASE_IMAGE=${motherhip_image_amd64} --platform linux/amd64"
    local arm64_build_args="--build-arg BASE_IMAGE=${motherhip_image_arm64} --platform linux/arm64"

    build_push_image "$amd64_build_args" "$amd64_target"
    build_push_image "$arm64_build_args" "$arm64_target"
  fi

  cd -
}


####################################
# 执行构建
####################################

if [ $command = "build" ]; then
  build_root
elif [ $command = "deploy" ]; then
  deploy_root
elif [ $command = "publish" ]; then
  publish_mothership
  publish_develop
elif [ $command = "publish-mothership" ]; then
  publish_mothership
elif [ $command = "publish-develop" ]; then
  publish_develop
elif [ $command = "preflight" ]; then
  preflight_release
elif [ $command = "probe-mothership" ]; then
  probe_mothership
elif [ $command = "probe-mothership-arm64" ]; then
  probe_mothership_arm64
elif [ $command = "probe-develop" ]; then
  probe_develop
fi

# 保存状态
if [ -n "$state_file" ]; then
  write_state_file
fi
