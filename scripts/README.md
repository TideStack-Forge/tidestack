# 自动化脚本仓库

## 1. ci

该文件夹中存放 CI 门控脚本。

| 文件名 | 说明 |
|-----------------------------|--------------------------------------|
| [ci/verify-ai-runtime-hygiene.sh](ci/verify-ai-runtime-hygiene.sh) | 验证 AI 运行时依赖卫生规则 |
| [ci/verify-statistics-reporting-e2e.sh](ci/verify-statistics-reporting-e2e.sh) | 验证 statistics-reporting E2E 测试套件（plan + run 模式） |

## 2. deploy

该目录保留历史独立部署脚本，不属于当前受支持的部署入口。当前部署请从[部署指南](../docs/deployment-guide.md)进入 Compose 正文；下面的历史验证描述不能证明当前版本可用。

| 文件 | 状态 |
| --- | --- |
| [deploy-develop-platform.sh](deploy/deploy-develop-platform.sh) | 历史脚本，默认版本及独立部署方式未随当前发行维护 |
| [deploy-develop-platform.bat](deploy/deploy-develop-platform.bat) | 同上 |

## 3. dev-runtime

该目录中存放开发运行时相关脚本。

| 文件名 | 说明 |
|-----------------------------|--------------------------------------|
| [dev-runtime/prepare-orbstack-k8s-smoke.sh](dev-runtime/prepare-orbstack-k8s-smoke.sh) | 为 OrbStack 本地 Kubernetes 冒烟准备上下文、namespace 和 PVC，并打印下一条 smoke 命令 |
| [dev-runtime/smoke-k8s-runtime.sh](dev-runtime/smoke-k8s-runtime.sh) | 对当前 worktree 的 Kubernetes 运行时实现执行真实集群冒烟验证，输出 `run.log` 和 `result.json` |

### `prepare-orbstack-k8s-smoke.sh`

用途：

- 校验当前 `kubectl` 上下文是否为本地 OrbStack 集群
- 确保 smoke 所需 namespace 与 PVC 已存在且 PVC 为 `Bound`
- 输出一条可直接复制执行的 `smoke-k8s-runtime.sh` 命令

可选环境变量：

- `K8S_SMOKE_CONTEXT`，默认 `orbstack`
- `K8S_SMOKE_NAMESPACE`，默认 `dev-runtime-smoke`
- `K8S_SMOKE_PVC`，默认 `dev-runtime-pvc`
- `K8S_SMOKE_PVC_SIZE`，默认 `1Gi`
- `K8S_SMOKE_STORAGE_CLASS`，默认空，表示使用集群默认 StorageClass
- `K8S_SMOKE_PREPARE_TIMEOUT_SECONDS`，默认 `60`
- `K8S_SMOKE_BINDER_IMAGE`，默认 `rancher/mirrored-library-busybox:1.36.1`，用于在 `WaitForFirstConsumer` 存储类下触发 PVC 绑定
- `K8S_SMOKE_IMAGE`，如果已提供则原样带入输出的 smoke 命令；否则脚本会尝试从本地 Docker 镜像中发现一个 mothership 候选

本地示例：

```bash
bash scripts/dev-runtime/prepare-orbstack-k8s-smoke.sh
K8S_SMOKE_IMAGE=registry.cn-hangzhou.aliyuncs.com/ouroboros/ouroboros-mothership:1.0.0-beta.2-SNAPSHOT-arm64 \
./scripts/dev-runtime/smoke-k8s-runtime.sh
```

使用独立 namespace/PVC 的示例：

```bash
K8S_SMOKE_NAMESPACE=dev-runtime-smoke-helper \
K8S_SMOKE_PVC=dev-runtime-pvc-helper \
bash scripts/dev-runtime/prepare-orbstack-k8s-smoke.sh
```

### `smoke-k8s-runtime.sh`

用途：

- 验证测试应用在 Kubernetes 后端下的 `TEMP_PORT` / `STABLE_DOMAIN` 生命周期
- 校验 `stop -> start -> restart` 以及租约清理链路
- 输出本地可读日志和 CI 可解析的结果 JSON

必填环境变量：

- `K8S_SMOKE_IMAGE`

可选环境变量：

- `K8S_SMOKE_NAMESPACE`，默认 `dev-runtime-smoke`
- `K8S_SMOKE_PVC`，默认 `dev-runtime-pvc`
- `K8S_SMOKE_INGRESS_SUFFIX`，默认 `smoke.local`
- `K8S_SMOKE_TIMEOUT_SECONDS`，默认 `120`
- `K8S_SMOKE_KEEP_ON_FAILURE`，默认 `false`
- `K8S_SMOKE_CLEANUP`，默认 `true`
- `K8S_SMOKE_OUTPUT_DIR`，默认 `tmp/dev-runtime/k8s-smoke/<timestamp>`

本地示例：

```bash
K8S_SMOKE_IMAGE=registry.cn-hangzhou.aliyuncs.com/ouroboros/ouroboros-mothership:1.0.0-beta.2-SNAPSHOT-arm64 \
./scripts/dev-runtime/smoke-k8s-runtime.sh
```

CI 示例：

```bash
export K8S_SMOKE_IMAGE=registry.cn-hangzhou.aliyuncs.com/ouroboros/ouroboros-mothership:1.0.0-beta.2-SNAPSHOT-arm64
export K8S_SMOKE_OUTPUT_DIR="$PWD/tmp/dev-runtime/k8s-smoke-ci"
./scripts/dev-runtime/smoke-k8s-runtime.sh
cat "$K8S_SMOKE_OUTPUT_DIR/result.json"
```

输出文件：

- `run.log`：完整运行日志
- `result.json`：结构化结果
- 失败时还会额外输出 `kubectl-get.txt`、`pods.yaml`、`events.txt`、`describe-pod.txt`

## 4. agent-feedback

该目录存放 Agent 反馈循环脚本。

| 文件名 | 说明 |
|-----------------------------|--------------------------------------|
| [agent-feedback/query-logs.sh](agent-feedback/query-logs.sh) | 查询后端 / 前端日志，并输出 Agent 友好的 JSONL |
| [agent-feedback/run-tests.sh](agent-feedback/run-tests.sh) | 运行架构测试、整仓测试或单模块测试，并写出 `result.json` |
| [agent-feedback/run-key-journey-e2e.sh](agent-feedback/run-key-journey-e2e.sh) | 执行关键用户旅程 E2E 入口脚本 |
| [agent-feedback/check-health.sh](agent-feedback/check-health.sh) | 轮询健康检查端点，按阈值判定启动是否成功 |
| [agent-feedback/query-metrics.sh](agent-feedback/query-metrics.sh) | 查询 Actuator metrics / Prometheus 暴露 |
| [agent-feedback/resume-session.sh](agent-feedback/resume-session.sh) | 输出近期提交、Git状态和按任务记录恢复的建议 |

## 5. open-source

该目录存放从 monorepo 导出开源仓库镜像、校验公开范围、以及把公开仓库变更回流到 monorepo 的脚本。脚本默认只做本地 dry-run / check，不会创建真实公开仓库、配置 remote、push、tag、release、publish 或 deploy。

| 文件名 | 说明 |
|-----------------------------|--------------------------------------|
| [open-source/export-data-model.sh](open-source/export-data-model.sh) | 按 `scripts/open-source/manifests/data-model/export-manifest.yaml` 生成数据模型 public tree，重写公开 Maven reactor，并写出 export/sync/import/gate report。 |
| [open-source/import-public-data-model-pr.sh](open-source/import-public-data-model-pr.sh) | 本地校验数据模型 public PR / fixture 回流范围，输出 import report，不自动 merge 或应用补丁。 |
| [open-source/check-public-forbidden-imports.sh](open-source/check-public-forbidden-imports.sh) | 对生成的数据模型 public tree 做 manifest-scoped forbidden import 和公开 API guard。 |
| [open-source/check-public-release-hardening.sh](open-source/check-public-release-hardening.sh) | 对生成的数据模型 public tree 做敏感信息、内部元数据、历史泄漏、required report 和 remote action hardening 检查。 |
| [open-source/check-pkgen-public-surface.sh](open-source/check-pkgen-public-surface.sh) | 只扫描数据模型 `data-pkgen-coding` 投影范围，避免 runtime/SQL 合法实现造成误报。 |
| [open-source/sync-ide-plugins.mjs](open-source/sync-ide-plugins.mjs) | 按 `ide-plugins/sync.manifest.json` 将 TideStack Develop 插件从 monorepo 单向同步到三个独立开源仓库，支持 `--check`、`--sync`、`--verify`、`--commit` 和 `--push`。 |

长期清单统一在 `scripts/open-source/manifests/data-model/` 与 `manifests/logicflow/`，是导出/边界工具的正式输入；不要从 `.codestable` 历史任务恢复维护副本。LogicFlow 单次报告和私有同步账本输出到 `tmp/testing-evidence/open-source/`，需保留时归任务/CI附件。旧 topology YAML 仅作历史来源，当前模块边界以正式 boundary 清单为准。

### 数据模型开源 dry-run

`check-public-release-hardening.sh` 需要先看到 export / import / sync 相关报告；本地没有真实 public PR 时，用 `--public-pr fixture` 生成回流边界报告。
`export-data-model.sh --mode sync` 可生成本地公开仓库投影和提交，但仍不会配置 remote、push、tag、release、publish 或 deploy。sync 模式默认使用公开维护者身份，且 public tree 不记录内部/本地 commit id；如需覆盖提交身份，可设置 `PUBLIC_GIT_USER_NAME` / `PUBLIC_GIT_USER_EMAIL`。
数据模型 public tree 的默认用户文档使用中文，并为 README、CONTRIBUTING、公开 API、adapter guide、最小示例和模块 README 生成对应 `.en.md` 英文副本。

```bash
bash scripts/open-source/export-data-model.sh \
  --manifest scripts/open-source/manifests/data-model/export-manifest.yaml \
  --target /tmp/ouroboros-data-model-public \
  --mode dry-run

bash scripts/open-source/check-public-forbidden-imports.sh \
  --manifest scripts/open-source/manifests/data-model/export-manifest.yaml \
  --public-tree /tmp/ouroboros-data-model-public

bash scripts/open-source/import-public-data-model-pr.sh \
  --public-repo /tmp/ouroboros-data-model-public \
  --public-pr fixture \
  --target-branch data-public-import-dry-run \
  --manifest scripts/open-source/manifests/data-model/export-manifest.yaml \
  --dry-run

bash scripts/open-source/check-public-release-hardening.sh \
  --manifest scripts/open-source/manifests/data-model/export-manifest.yaml \
  --public-tree /tmp/ouroboros-data-model-public
```

### LogicFlow 开源 dry-run

| 文件名 | 说明 |
|-----------------------------|--------------------------------------|
| [open-source/logicflow/bootstrap-public-repo-dry-run.sh](open-source/logicflow/bootstrap-public-repo-dry-run.sh) | 生成 history-free LogicFlow public repo 本地快照，输出 root parent、BOM、公开模块、中文默认 README/CONTRIBUTING/docs、英文 `.en.md` 副本、LICENSE/NOTICE、CI stub 和 sample。 |
| [open-source/logicflow/sync-public-repo-dry-run.sh](open-source/logicflow/sync-public-repo-dry-run.sh) | 重复导出 LogicFlow public snapshot 并比较 hash，生成 private sync ledger 和 public commit plan。 |
| [open-source/logicflow/run-governance-gates.sh](open-source/logicflow/run-governance-gates.sh) | 运行 LogicFlow dependency/source/history/secret/build/sync governance gates。 |
| [open-source/logicflow/run-release-dry-run.sh](open-source/logicflow/run-release-dry-run.sh) | 创建 target 下的本地 throwaway public repo，运行 repo mode governance gates、public build/test 和 release dry-run 报告。 |
| [open-source/logicflow/import-public-patch-dry-run.sh](open-source/logicflow/import-public-patch-dry-run.sh) | 对 LogicFlow public patch 做路径分类，公开候选路径生成 apply plan，内部 adapter 路径阻断。 |
| [open-source/logicflow/check-public-repo-leaks.sh](open-source/logicflow/check-public-repo-leaks.sh) | 检查 LogicFlow public tree 是否包含内部路径、禁止 import、禁止 Maven artifact、Git ancestry 或内部提交标记。 |

```bash
bash scripts/open-source/logicflow/bootstrap-public-repo-dry-run.sh --check
bash scripts/open-source/logicflow/sync-public-repo-dry-run.sh --check
bash scripts/open-source/logicflow/run-governance-gates.sh --check
bash scripts/open-source/logicflow/run-release-dry-run.sh --check
```

## 新增组件分发

脚本组件与官方项目模板的本地导出、差异检查和更新命令见[开源导出说明](open-source/README.md)。已有 Data、LogicFlow 和 IDE 分发继续沿用各自正式入口。
