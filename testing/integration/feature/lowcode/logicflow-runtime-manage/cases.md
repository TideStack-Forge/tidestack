# 用例表

| Case ID | 可观察断言 | 实现/执行入口 |
| --- | --- | --- |
| LF-RM-CLUSTER-001 | 两个 bootId 各自报告目标代次已应用，父流程执行覆盖后的子流 | acceptance.py verify-api |
| LF-RM-CAS-001 | 同 operationId 同请求重放；旧 editToken 409；只读身份写入 403 | acceptance.py verify-api |
| LF-RM-INVALID-001 | 缺失引用候选 valid=false、保存 422、旧完成态仍执行 | acceptance.py verify-api |
| LF-RM-RESTART-001 | 第二个 JVM 重启生成新 bootId，启动直接加载持久覆盖 | acceptance.py verify-api |
| LF-RM-REVOKE-001 | 撤销后两实例父流程执行部署默认 | acceptance.py verify-api |
| LF-RM-UI-001 | 真实列表 → 详情 → 编辑 → 检查 → 保存 → 回执及实例代次；截图 | runtime-manage-browser.jsx + agent-browser |
| LF-RM-LAZY-001 | 首次开图前无 canvas/debug/preview 请求；首次开图加载 canvas，runtime 不加载 debug | 同上，资源时间线证据 |
| LF-RM-RETRY-001 | canvas 请求失败保留表单，人工重试成功；关闭加载不挂载子树 | 浏览器 + lazy-renderer.integration.test.js |
| LF-RM-READONLY-001 | 只读身份无写操作；查看图无节点增删、粘贴、拖动修改 | 浏览器 + Canvas / renderer integration |
| LF-RM-ASSETS-001 | 新旧版本目录并存；旧页在新版生效后仍请求旧 canvas 成功 | 浏览器版本目录验收 |

| LF-RM-OBS-DEFAULT-001 | 缺省流程两个开关关闭 | verify_observation.py |
| LF-RM-OBS-INDEPENDENT-001 | 仅子流采集，两个 JVM 各一份；模型发布不变 | verify_observation.py |
| LF-RM-OBS-SWAP-001 | 仅父流监控，子流关闭不新增，父流无详细样本 | verify_observation.py |
| LF-RM-OBS-BOTH-001 | 父子分别采集并正确关联，无重复统计 | verify_observation.py |
| LF-RM-OBS-CAS-001 | 配置旧修订 409、只读 403、未知应用字段 400 | verify_observation.py |
| LF-RM-OBS-RESTART-001 | 重启后保留策略及原诊断截止，boot 标识改变 | verify_observation.py |
| LF-RM-OBS-STOP-001 | 关闭后停止新增，历史仍可读 | verify_observation.py |
| LF-RM-OBS-UI-001 | 默认关闭、设置表单、节点时序、状态/读取失败、只读与窄屏截图 | 正式 AMIS schema + agent-browser |

全部执行分类、断言级别、pass/fail/blocked 和证据路径写入本轮任务报告；表中入口存在本身不构成通过。
