# Metadata And High-Code Data API Regression Cases

当前阶段先把这组场景纳入统一测试体系，并明确桥接边界。详细的大型生成用例表仍由现有回归引擎输出到 `tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs`，后续再逐步迁移成场景自持有的细粒度用例资产。

| Case ID | Objective | Preconditions / Input | Mode | Pass Criteria | Evidence |
| --- | --- | --- | --- | --- | --- |
| DATAAPI-INV-001 | 生成 metadata + high-code inventory 和 cases 报告 | app-root 可访问 | e2e | 新 runner 能成功调起 inventory-only 模式 | `tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs/*inventory*`, `tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs/*test-cases*` |
| DATAAPI-META-LIST-001 | 执行 metadata list ring | develop 运行时可访问，管理员身份可登录 | e2e | 生成新的 live 执行结果并回填 summary | `results.json`, summary report |
| DATAAPI-META-DETAIL-001 | 执行 metadata detail ring | 同模型 list 可先取到详情 id | e2e | `list -> first id -> detail` 链路被真实执行并记录 | `results.json`, summary report |
| DATAAPI-META-QUERY-001 | 执行 metadata query-condition ring | 需要从过滤器配置推导参数形态 | e2e | 查询条件分支可被统一 runner 调起 | `results.json`, summary report |
| DATAAPI-META-CREATE-001 | 执行 metadata create ring | 目标模型满足 stage-one write executable | e2e | create 路径可执行并记录结果 | `results.json` |
| DATAAPI-META-UPDATE-001 | 执行 metadata update ring | 已有可写样本和可更新字段 | e2e | update 路径可执行并记录结果 | `results.json` |
| DATAAPI-META-DELETE-001 | 执行 metadata delete ring | 已有可删样本 | e2e | delete 路径可执行并记录结果 | `results.json` |
| DATAAPI-HC-INV-001 | 盘点高代码数据 API 并纳入统一清单 | controller 源码可扫描 | harness | 高代码种子进入 inventory / cases 输出 | inventory report, cases report |
| DATAAPI-HC-READ-001 | 执行高代码主门禁 GET ring | 基座运行时可访问，管理员身份可登录 | e2e | `high-code-read` ring 可执行并记录结果 | `results.json`, summary report |
| DATAAPI-HC-SPECIAL-READ-001 | 执行高代码专项 GET ring | develop 或上下文用户场景已准备 | harness | `high-code-special-read` ring 可执行并记录结果 | `results.json`, findings |
| DATAAPI-HC-WRITE-001 | 执行高代码主门禁写 ring | seed 已具备 `sample_payload + cleanup`，运行时可登录 | e2e | `high-code-write` ring 只对 execution-ready seed 执行并记录结果 | `results.json`, summary report |
| DATAAPI-HC-SPECIAL-WRITE-001 | 执行高代码专项写 ring | develop 或特定上下文场景已准备，seed 已 execution-ready | harness | `high-code-special-write` ring 可执行并记录结果 | `results.json`, findings |
| DATAAPI-PERM-001 | 对权限不足结果单独分类 | 准备无权限身份或缺失上下文 | harness | 权限边界与代码回归分开统计 | summary report, live findings |
| DATAAPI-TRUTH-001 | 校验资产真相先于覆盖率统计 | 检查主子表识别、stage-one write 分母、高代码 pending seed 列表 | harness | 报告能区分 asset-present / execution-ready / pending，不靠伪分母抬高覆盖率 | inventory report, summary report |
| DATAAPI-FIXTURE-001 | 校验 fixture 预检先于场景执行 | data/runtime/identity fixture 已声明 | harness | runner 先产出 `fixture-preflight.json`，并在 fixture 失真时阻断后续执行 | `fixture-preflight.json` |
| DATAAPI-PREFLIGHT-001 | 校验 runtime preflight 双层基线 | 目标 runtime 可访问 | harness | preflight 同时覆盖 `/api/auth/authorities -> 401` 和本地类路径完整性 | summary report, results.json |

## Transition Note

当前 `cases.md` 先承担场景级用例入口职责，详细生成矩阵仍由旧引擎输出到证据目录：

- 当前默认出口：`tmp/testing-evidence/metadata-high-code-data-api-regression/default-docs`
- 历史快照不保证存在于当前 checkout；需要历史证据时，从原任务/CI 或 Git 固定版本查找，不据路径推定已归档

下一阶段会把细粒度用例表逐步迁回场景目录，并继续补齐：

- stage-one write 样本构造能力
- pending 高代码 seed 的 execution-ready 条件
- 资产真相校验的自动化报告
