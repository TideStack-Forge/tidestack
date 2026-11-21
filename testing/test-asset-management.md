# Test Asset Management

这份文档定义 `testing/` 下测试资产如何新增、分类、演进、归档和清理。

如果你要看“测试任务本身如何管理、如何分类、如何形成结论”，请先看 [testing/test-management-standard.md](test-management-standard.md)。

## 一、管理目标

目标不是“把所有测试都搬进一个目录”，而是让以下几类资产有清晰边界：

- 标准文档
- 正式场景
- 公共夹具与 runner
- 一次性运行证据
- 历史经验文章

## 二、目录职责

- `testing/unified-testing-playbook.md`
  - 放综合执行标准
- `testing/test-management-standard.md`
  - 放测试管理规范
- `testing/test-asset-management.md`
  - 放治理规范
- `testing/integration/`
  - 放集成测试场景契约、用例表、harness playbook
- `testing/e2e/`
  - 放自动化 E2E manifest
- `testing/common/fixtures/`
  - 放共享夹具
- `testing/common/runners/`
  - 放共享 runner、bridge、预检器
- `testing/common/templates/`
  - 放新场景/夹具模板
- `tmp/testing-evidence/`
  - 放一次性执行产物与分析证据
- `tmp/`
  - 放本地运行时目录、scratch 副本、临时 app-root、调试输出等一次性本地状态

## 三、分类规则

先按测试类型分层：

- `integration/`
- `e2e/`

再按测试目的分类：

- `smoke/`
- `regression/`
- `feature/`

约定如下：

- `smoke`
  - 用于验证主链路是否可用，覆盖范围窄但必须稳定
- `regression`
  - 用于重大改动、合并前回归、兼容性验证
- `feature`
  - 用于新增功能、口头指定用例、临时探索场景
  - 先走 harness，稳定后再晋升为 `smoke` 或 `regression`

## 四、一个新场景如何进入体系

新增场景时，顺序必须是：

1. 在 `testing/integration/**` 下创建场景目录
2. 补齐：
   - `scenario.yaml`
   - `contract.md`
   - `cases.md`
3. 如果需要 AI agent / 人工辅助执行，再补：
   - `harness.playbook.md`
4. 如果已经具备稳定自动化条件，再在 `testing/e2e/**` 下补对应 manifest
5. 如果出现新的共享依赖，再抽象到 `testing/common/fixtures/`
6. 如果出现新的通用执行逻辑，再抽象到 `testing/common/runners/`

场景命名还必须满足：

- 让没有上下文的人也能判断目标对象和边界
- 优先写“对象 + 目标 + 边界”，不要只写“database / feature / smoke”这类大类词

禁止直接从以下内容反向生成正式场景：

- 零散聊天记录
- 临时 shell 命令集合
- 某次运行目录里的结果文件

## 五、什么时候新增 fixture

只有当某个依赖或上下文满足以下条件时，才应进入 `testing/common/fixtures/`：

- 能被多个场景复用
- 输入输出边界清晰
- 能做基础健康检查
- 生命周期语义明确

以下内容不应直接进公共夹具：

- 某次临时调试用的单次命令
- 强耦合某个场景的临时样本拼装
- 无法复用的本地私人目录

## 六、什么时候新增 runner

只有当“执行逻辑”具备跨场景复用价值时，才应新增到 `testing/common/runners/`，例如：

- fixture 预检
- 生命周期编排
- 浏览器资产 bridge
- 旧系统回归脚本桥接器

场景特有逻辑不应先写成公共 runner，而应先留在场景 `harness.playbook.md` 或模块内测试实现里。

## 七、证据如何管理

跨模块测试的一次性证据默认进入 `tmp/testing-evidence/`；正式 runner 已约定的 `tmp/agent-feedback/`、Playwright 报告目录等可保留，并在任务交付中链接实际位置。清理前将需长期追溯的证据保存到任务或 CI 制品。证据包括：

- logs
- screenshots
- traces
- inventory / cases / summary
- findings / root-cause notes

证据目录的目标是：

- 可追溯
- 可审计
- 可复盘

证据目录的目标不是：

- 成为长期事实来源
- 取代场景契约
- 替代公共夹具或 runner

除证据外，以下本地状态也应优先收口到 `tmp/`，并默认不纳入版本库：

- 本地 runtime 工作目录
- 临时 app-root 副本
- 调试脚本输出
- 一次性 smoke / harness / e2e scratch 目录

原则是：

- `tmp/testing-evidence/` 放“这次跑出了什么”
- `tmp/` 其他目录放“这次为了跑测试临时搭了什么”

## 八、历史文章如何处理

单次经验、评审和验证过程保存在关联任务/PR/Issue 或固定提交历史，不在长期文档目录维护 articles 档案。处理时遵循：

- 已经被 `testing/` 吸收的稳定结论以当前正文为准，原过程材料仅用于追溯
- 新的规范、路径、命令、分类规则，以 `testing/` 为准
- 清理时优先做“引用收敛”和“角色降级”，不要一上来硬删原始经验

## 九、清理规则

当出现以下情况时，应考虑清理或迁移：

- 同一类执行规则在多个位置重复出现
- 旧目录仍保留，但已不再是真实入口
- 临时夹具已经被正式夹具替代
- 某个场景已经稳定为正式 smoke / regression，但旧的 ad-hoc 说明还在误导使用者

清理顺序建议：

1. 先建立新的正式入口
2. 再把旧入口改成引用或桥接
3. 最后删除完全失效的重复资产

## 十、AI Agent 规则

联调、harness、smoke、regression、E2E 相关任务，默认先看：

- [docs/rules/testing.md](../docs/rules/testing.md)
- [testing/test-management-standard.md](test-management-standard.md)
- [testing/unified-testing-playbook.md](unified-testing-playbook.md)
- 对应场景所在的 `testing/integration/**` 或 `testing/e2e/**`

不要把历史文章、零散脚本或临时结果目录当成当前事实来源。
