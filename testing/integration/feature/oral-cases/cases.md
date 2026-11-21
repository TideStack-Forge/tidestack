# Oral Feature Cases

这组场景用于把用户临时口述或聊天补充的验证要求，先转写成结构化用例，再执行 harness 验证。

| case_id | title | preconditions | mode | expected | evidence |
| --- | --- | --- | --- | --- | --- |
| ORAL-CASE-001 | 记录本轮 feature 主链路 | 明确本轮功能范围、入口、运行时、账号 | harness | 能说明本轮要验证什么、不验证什么 | `tmp/testing-evidence/feature-oral-cases/**` |
| ORAL-CASE-002 | 转写口头用例并执行 | 口头或聊天给出的步骤已补到本表 | harness | 结果按环境/缺陷/待补资产分类 | `tmp/testing-evidence/feature-oral-cases/**` |
| ORAL-CASE-003 | 抽象可复用经验 | 本轮执行结束 | harness | 能判断是否升级为 smoke/regression 或抽象为公共 fixture | `testing/test-asset-management.md`, `testing/unified-testing-playbook.md` |
